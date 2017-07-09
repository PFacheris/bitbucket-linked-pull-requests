package me.facheris.bitbucket.plugins.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;

import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.pluginsettings.PluginSettings;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;

import javax.xml.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Represents all links to/from a specific pull request.
 */
@XmlAccessorType(XmlAccessType.NONE)
public class LinkedPullRequest {

    private static final Logger log = LoggerFactory.getLogger(LinkedPullRequest.class);

    public final PullRequest root;
    private final PluginSettings pluginSettings;
    private final PullRequestService pullRequestService;

    private LinkedPullRequest() {
        this.root = null;
        this.pluginSettings = null;
        this.pullRequestService = null;
    }

    public LinkedPullRequest(
        PullRequest root,
        PluginSettings pluginSettings,
        PullRequestService pullRequestService
    ) {
        this.root = root;
        this.pluginSettings = pluginSettings;
        this.pullRequestService = pullRequestService;
    }

    @XmlElementRef(name="links")
    public List<PullRequestLink> getLinks() {
        List<String> linksRaw = this.getPluginSettingsList(
            LinkedPullRequest.getStorageKey(this.root)
        );
        // Convert raw list to List of PullRequestLinks by deserializing strings
        List<PullRequestLink> links = new ArrayList<PullRequestLink>();
        PullRequestLink link = null;
        for (String s : linksRaw) {
            // The below line also performs a check to ensure that the
            // pull request referenced in storage still exists, returning
            // null if not.
            link = PullRequestLink.deserialize(this.pullRequestService, s);
            if (link != null) {
                links.add(link);
            } else {
                // TODO: Purge invalid entries from the list
            }
        }
        return links;
    }

    public PullRequestLink getById(UUID id) {
        for (PullRequestLink link : this.getLinks()) {
            if (link.id.equals(id)) {
                return link;
            }
        }
        return null;
    }

    public PullRequestLink getByPullRequest(PullRequest pullRequest) {
        for (PullRequestLink link : this.getLinks()) {
            if (LinkedPullRequest.isSamePullRequest(link.target, pullRequest)) {
                return link;
            }
        }
        return null;
    }

    private static boolean isSamePullRequest(PullRequest a, PullRequest b) {
        return a.getToRef().getRepository().getId() == b.getToRef().getRepository().getId() &&
            a.getId() == b.getId();
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

    public PullRequestLink createLink(
        PullRequest toPullRequest, PullRequestLink.Direction direction
    ) throws IllegalArgumentException {
        // Don't allow links to be created to self
        if (LinkedPullRequest.isSamePullRequest(toPullRequest, this.root)) {
            throw new IllegalArgumentException(
                "Pull request cannot be linked to itself."
            );
        }
        // External calls to createLink should affect both sides of the link
        // so we wrap the method that actually does the work
        PullRequestLink link = this.createLinkSimple(toPullRequest, direction);
        if (link == null) {
            throw new IllegalArgumentException(
                "Pull request link already exists."
            );
        }

        // Now create a link on the LinkedPullRequest rooted at toPullRequest
        // with the appropriate direction
        PullRequestLink.Direction oppositeDirection =
            (direction == PullRequestLink.Direction.TO) ?
            PullRequestLink.Direction.FROM : PullRequestLink.Direction.TO;
        LinkedPullRequest targetLinkedPullRequest = new LinkedPullRequest(
            toPullRequest, this.pluginSettings, this.pullRequestService
        );
        targetLinkedPullRequest.createLinkSimple(this.root, oppositeDirection);
        return link;
    }

    public PullRequestLink createLinkSimple(
        PullRequest toPullRequest, PullRequestLink.Direction direction
    ) {
        if (direction == PullRequestLink.Direction.BIDIRECTIONAL) {
            throw new IllegalArgumentException(
                "Cannot create a BIDIRECTIONAL link directly, this is " +
                "automatically set when a link already exists of the opposite " +
                "direction."
            );
        }
        // Create PullRequestLink if one does not already exist
        PullRequestLink link = this.getByPullRequest(toPullRequest);
        if (link != null) {
            // Update the direction of the existing link if necessary,
            // there's no need to update a bidirectional link, it can only
            // be changed via removal
            if (
                link.direction == PullRequestLink.Direction.BIDIRECTIONAL &&
                direction != link.direction
            ) {
                link.direction = PullRequestLink.Direction.BIDIRECTIONAL;
            } else {
                // No changes
                return null;
            }
            this.removeLink(link.id);
        } else {
            link = new PullRequestLink(toPullRequest, direction);
        }
        
        this.settingsListInsert(
            LinkedPullRequest.getStorageKey(this.root),
            link.serialize()
        );
        return link;
    }

    public PullRequestLink removeLink(UUID id) {
        PullRequestLink link = this.getById(id);
        if (link == null) {
            return null;
        }
        this.settingsListRemove(
            LinkedPullRequest.getStorageKey(this.root),
            link.serialize()
        );
        return link;
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
