package me.facheris.bitbucket.plugins;

import java.util.ArrayList;
import java.util.List;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;

import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.pluginsettings.PluginSettings;

import org.json.JSONObject;

import javax.xml.bind.annotation.*;


@Scanned
@XmlRootElement(name = "linked-pull-requests")
@XmlAccessorType(XmlAccessType.NONE)
public class LinkedPullRequests{
    private static final String PLUGIN_STORAGE_KEY = "me.facheris.bitbucket.plugins.linked-pull-requests";
    private final PluginSettings pluginSettings;
    private final PullRequest root;
    private final PullRequestService pullRequestService;

    // This private constructor isn't used by any code, but JAXB requires any
    // representation class to have a no-args constructor.
    private LinkedPullRequests(
        @ComponentImport PluginSettingsFactory pluginSettingsFactory,
        @ComponentImport PullRequestService pullRequestService
    ) {
        this.pluginSettings = pluginSettingsFactory.createSettingsForKey(
            PLUGIN_STORAGE_KEY
        );
        this.pullRequestService = pullRequestService;
        this.root = null;
    }

    public LinkedPullRequests(
        PullRequest root,
        PluginSettings pluginSettings,
        PullRequestService pullRequestService
    ) {
        this.root = root;
        this.pluginSettings = pluginSettings;
        this.pullRequestService = pullRequestService;
    }

    @XmlElementRef(name="to")
    public List<PullRequest> getTo() {
        List<String> linksToRaw = this.getPluginSettingsList(
            LinkedPullRequests.getLinksToStorageKey(this.root)
        );
        return deserializeRawSettingsList(linksToRaw);
    }

    @XmlElementRef(name="from")
    public List<PullRequest> getFrom() {
        List<String> linksFromRaw = this.getPluginSettingsList(
            LinkedPullRequests.getLinksFromStorageKey(this.root)
        );
        return deserializeRawSettingsList(linksFromRaw);
    }

    private List<String> getPluginSettingsList(String key) {
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

    private static String getStorageKey(PullRequest pullRequest) {
        int repositoryId = pullRequest.getToRef().getRepository().getId();
        long pullRequestId = pullRequest.getId();
        return Integer.toString(repositoryId) + "#" + Long.toString(pullRequestId);
    }

    public static String getLinksToStorageKey(PullRequest pullRequest) {
        return LinkedPullRequests.getStorageKey(pullRequest) + ".links-to";
    }

    public static String getLinksFromStorageKey(PullRequest pullRequest) {
        return LinkedPullRequests.getStorageKey(pullRequest) + ".links-from";
    }

    public void createLink(PullRequest fromPullRequest, PullRequest toPullRequest) {
        this.settingsListInsert(
            LinkedPullRequests.getLinksFromStorageKey(toPullRequest),
            this.serializeSettingsListEntry(fromPullRequest)
        );
        try {
            this.settingsListInsert(
                LinkedPullRequests.getLinksToStorageKey(fromPullRequest),
                this.serializeSettingsListEntry(toPullRequest)
            );
        } catch (Exception e) {
            // If a failure occurred here we want to make sure the link stays
            // in a bi-directional state, so we should remove the previously
            // added one-way link.
            this.settingsListRemove(
                LinkedPullRequests.getLinksFromStorageKey(toPullRequest),
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
        int repositoryId = json.getInt("repositoryId");
        long pullRequestId = json.getLong("pullRequestId");
        return this.pullRequestService.getById(repositoryId, pullRequestId);
    }

    private List<PullRequest> deserializeRawSettingsList(List<String> linkedPullRequestsRaw) {
        // Convert raw list to List of PullRequests by deserializing strings
        List<PullRequest> linkedPullRequests = new ArrayList<PullRequest>();
        PullRequest linkedPullRequest = null;
        for (String s : linkedPullRequestsRaw) {
            linkedPullRequest = this.deserializeSettingsListEntry(s);
            if (linkedPullRequest != null) {
                linkedPullRequests.add(linkedPullRequest);
            }
        }
        return linkedPullRequests;
    }

    private void settingsListInsert(String key, String entry) {
        List<String> entries = this.getPluginSettingsList(key);
        entries.add(entry);
        this.pluginSettings.put(key, entries);
    }

    private void settingsListRemove(String key, String entry) {
        List<String> entries = this.getPluginSettingsList(key);
        entries.remove(entry);
        this.pluginSettings.put(key, entries);
    }
}
