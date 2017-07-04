package me.facheris.bitbucket.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import org.springframework.web.bind.annotation.RequestParam;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.repository.Repository;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
 

@Scanned
@Path("/hello")
public class LinkedPullRequestRestResource
{
    private static final String PLUGIN_STORAGE_KEY = "me.facheris.bitbucket.plugins.linked-pull-requests";

    @ComponentImport
    private final RepositoryService repositoryService;
    @ComponentImport
    private final PullRequestService pullRequestService;
    private final PluginSettings pluginSettings;

    public LinkedPullRequestRestResource(
        @ComponentImport PluginSettingsFactory pluginSettingsFactory,
        RepositoryService repositoryService,
        PullRequestService pullRequestService
    ) {
        this.pluginSettings = pluginSettingsFactory.createSettingsForKey(
            PLUGIN_STORAGE_KEY
        );
        this.repositoryService = repositoryService;
        this.pullRequestService = pullRequestService;
    } 

    @GET
    @Path("/world")
    @Produces("text/html")
    public Response getHello() {
        return Response.ok("test").build();
    }

//    @GET
//    @Path("projects/{project}/repos/{slug}/pull-requests/{pullRequestId}")
//    @Produces({MediaType.APPLICATION_JSON})
//    public Response getLinkedPullRequests(
//        @PathParam("project") String projectKey,
//        @PathParam("slug") String slug,
//        @PathParam("pullRequestId") Long pullRequestId
//    ) {
//        PullRequest pullRequest = this.getPullRequest(projectKey, slug, pullRequestId); 
//
//        return Response.status(200)
//            .entity(new LinkedPullRequests(
//                pullRequest, this.pluginSettings, this.pullRequestService
//            ))
//            .build();
//    }
//
//    @POST
//    @Path("projects/{project}/repos/{slug}/pull-requests/{pullRequestId}")
//    public Response createLinkedPullRequest(
//        @PathParam("project") String projectKey,
//        @PathParam("slug") String slug,
//        @PathParam("pullRequestId") Long pullRequestId,
//        @RequestParam("to") LinkedPullRequest toLinkedPullRequest,
//        @RequestParam("from") LinkedPullRequest fromLinkedPullRequest
//    ) {
//        PullRequest pullRequest = this.getPullRequest(projectKey, slug, pullRequestId); 
//
//        PullRequest toPullRequest = this.pullRequestService.getById(
//            toLinkedPullRequest.repositoryId, toLinkedPullRequest.pullRequestId       
//        );
//        if (toPullRequest == null) {
//            throw new NotFoundException();
//        }
//
//        PullRequest fromPullRequest = this.pullRequestService.getById(
//            fromLinkedPullRequest.repositoryId, fromLinkedPullRequest.pullRequestId       
//        );
//        if (fromPullRequest == null) {
//            throw new NotFoundException();
//        }
//        LinkedPullRequests linkedPullRequests = new LinkedPullRequests(
//            pullRequest, this.pluginSettings, this.pullRequestService
//        );
//        linkedPullRequests.createLink(fromPullRequest, toPullRequest);
//        return Response.status(201).build();
//    }
//
//    private PullRequest getPullRequest(
//        String projectKey, String slug, Long pullRequestId
//    ) throws NotFoundException {
//        Repository repo = this.repositoryService.getBySlug(projectKey, slug);
//        if (repo == null) {
//            throw new NotFoundException();
//        }
//
//        PullRequest pullRequest = this.pullRequestService.getById(
//            repo.getId(), pullRequestId
//        );
//        if (pullRequest == null) {
//            throw new NotFoundException();
//        }
//        return pullRequest;
//    }

}
