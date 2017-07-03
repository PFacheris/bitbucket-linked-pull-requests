package me.facheris.bitbucket.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import javax.inject.Inject;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;

import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.HTTP;
 

@Scanned
public class LinkedPullRequestServlet extends HttpServlet
{
    private static final String PLUGIN_STORAGE_KEY = "me.facheris.bitbucket.plugins.linked-pull-requests";
    @ComponentImport
    private final UserManager userManager;
    @ComponentImport
    private final LoginUriProvider loginUriProvider;
    @ComponentImport
    private final PullRequestService pullRequestService;

    private final PluginSettings pluginSettings;

    @Inject
    public LinkedPullRequestServlet(
        UserManager userManager, LoginUriProvider loginUriProvider,
        @ComponentImport PluginSettingsFactory pluginSettingsFactory,
        PullRequestService pullRequestService
    ) {
        this.userManager = userManager;
        this.loginUriProvider = loginUriProvider;
        this.pluginSettings = pluginSettingsFactory.createSettingsForKey(
            PLUGIN_STORAGE_KEY
        );
        this.pullRequestService = pullRequestService;
    } 

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        String username = userManager.getRemoteUsername(request);
        if (username == null || !userManager.isSystemAdmin(username))
        {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String repositoryIdRaw = request.getParameter("repositoryId");
        String pullRequestIdRaw = request.getParameter("pullRequestId");
        if (repositoryIdRaw == null || pullRequestIdRaw == null) {
            // We need to iterate over all keys in this namespace to generate
            // the full API response.
            // TODO: Implement this with pluginSettings.asMap().keySet()
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Listing all linked pull requests is not yet supported " +
                "be sure to include the 'repositoryId' and 'pullRequestId' " +
                "parameters."
            );
            return;
        }

        int repositoryId;
        long pullRequestId;
        try {
            repositoryId = Integer.parseInt(repositoryIdRaw);
            pullRequestId = Integer.parseInt(pullRequestIdRaw);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "'repositoryId' and 'pullRequestId' must be integer values."
            );
            return;
        }

        List<String> linksTo = this.getPluginSettingsSafe(this.createLinksToKey(
            repositoryId, pullRequestId
        ));

        List<String> linksFrom = this.getPluginSettingsSafe(this.createLinksFromKey(
            repositoryId, pullRequestId           
        ));

        Map<String, Object> context = new HashMap<String, Object>();
        context.put("to", linksTo);
        context.put("from", linksFrom);
        JSONObject json = new JSONObject(context);

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        json.write(response.getWriter());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (request.getContentType() != "application/json") {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                "Request content-type must be application/json"
            );
        }

        StringBuffer jb = new StringBuffer();
        String line = null;
        try {
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                jb.append(line);
            }
        } catch (Exception e) {
            /*report an error*/
        }

        JSONObject json;
        try {
            json = HTTP.toJSONObject(jb.toString());
        } catch (JSONException e) {
          // crash and burn
          throw new IOException("Error parsing JSON request string");
        }

        if (!json.keySet().containsAll(Arrays.asList("to", "from"))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Request body must contain 'to' and 'from' keys containing " +
                "pull request information."
            );
        }

        JSONObject toJson = json.getJSONObject("to");
        JSONObject fromJson = json.getJSONObject("from");
        if (!isValidPullRequestJson(toJson) || !isValidPullRequestJson(fromJson)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "'to' and 'from' fields must each contain 'repositoryId' " +
                "and 'pullRequestId'."
            );
        }
        PullRequest toPullRequest = getPullRequestFromJson(toJson);
        if (toPullRequest == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                "Pull request referenced in the 'to' field does not exist."
            );
        }
        PullRequest fromPullRequest = getPullRequestFromJson(fromJson);
        if (fromPullRequest == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                "Pull request referenced in the 'from' field does not exist."
            );
        }
        this.createLink(fromPullRequest, toPullRequest);
        response.setStatus(HttpServletResponse.SC_CREATED);
    }

    private List<String> getPluginSettingsSafe(String key) {
        Object obj = this.pluginSettings.get(key);
        if (obj instanceof List<?>) {
            // The unchecked cast warning due to the below line should be
            // fine as pluginSettings only supports List<String>
            return (List<String>) obj;
        } else {
            // Delete what's currently there and return an empty list
            this.pluginSettings.remove(key);
            return new ArrayList<String>();
        }
    }

    private String createKey(int repositoryId, long pullRequestId) {
        return Integer.toString(repositoryId) + "#" + Long.toString(pullRequestId);
    }

    public String createLinksToKey(int repositoryId, long pullRequestId) {
        return this.createKey(repositoryId, pullRequestId) + ".links-to";
    }

    public String createLinksFromKey(int repositoryId, long pullRequestId) {
        return this.createKey(repositoryId, pullRequestId) + ".links-from";
    }

    private void createLink(PullRequest fromPullRequest, PullRequest toPullRequest) {
        this.settingsListInsert(
            this.createLinksFromKey(
                toPullRequest.getToRef().getRepository().getId(),
                toPullRequest.getId()
            ),
            this.serializeSettingsListEntry(fromPullRequest)
        );
        try {
            this.settingsListInsert(
                this.createLinksToKey(
                    fromPullRequest.getToRef().getRepository().getId(),
                    fromPullRequest.getId()
                ),
                this.serializeSettingsListEntry(toPullRequest)
            );
        } catch (Exception e) {
            // If a failure occurred here we want to make sure the link stays
            // in a bi-directional state, so we should remove the previously
            // added one-way link.
            this.settingsListRemove(
                this.createLinksFromKey(
                    toPullRequest.getToRef().getRepository().getId(),
                    toPullRequest.getId()
                ),
                this.serializeSettingsListEntry(fromPullRequest)
            );
        }
    }

    private String serializeSettingsListEntry(PullRequest pullRequest) {
        JSONObject json = new JSONObject();
        json.put("repositoryId", pullRequest.getToRef().getRepository().getId());
        json.put("pullRequestId", pullRequest.getId());
        return json.toString();
    }

    private PullRequest deserializeSettingsListEntry(String entry) {
        JSONObject json = new JSONObject(entry);
        return this.getPullRequestFromJson(json);
    }

    private void settingsListInsert(String key, String entry) {
        List<String> entries = this.getPluginSettingsSafe(key);
        entries.add(entry);
        this.pluginSettings.put(key, entries);
    }

    private void settingsListRemove(String key, String entry) {
        List<String> entries = this.getPluginSettingsSafe(key);
        entries.remove(entry);
        this.pluginSettings.put(key, entries);
    }

    private boolean isValidPullRequestJson(JSONObject json) {
        return json.keySet().containsAll(
            Arrays.asList("repositoryId", "pullRequestId")
        );
    }

    private PullRequest getPullRequestFromJson(JSONObject json) {
        int repositoryId = json.getInt("repositoryId");
        long pullRequestId = json.getLong("pullRequestId");
        return this.pullRequestService.getById(repositoryId, pullRequestId);
    }

}
