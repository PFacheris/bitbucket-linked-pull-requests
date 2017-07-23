package me.facheris.bitbucket.plugins.rest;

import me.facheris.bitbucket.plugins.models.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import org.springframework.web.bind.annotation.RequestBody;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.repository.Repository;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@Scanned
@Path("/projects/{project}/repos/{slug}/pull-requests/{pullRequestId}")
public class LinkedPullRequestsRestResource
{
    private static final String STORAGE_KEY = "me.facheris.bitbucket.plugins.linked-pull-requests";
    @ComponentImport
    private final RepositoryService repositoryService;
    @ComponentImport
    private final PullRequestService pullRequestService;
    private final PluginSettings pluginSettings;

    public LinkedPullRequestsRestResource(
        @ComponentImport PluginSettingsFactory pluginSettingsFactory,
        RepositoryService repositoryService,
        PullRequestService pullRequestService
    ) {
        this.pluginSettings = pluginSettingsFactory.createSettingsForKey(
            STORAGE_KEY
        );
        this.repositoryService = repositoryService;
        this.pullRequestService = pullRequestService;
    } 

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response getLinkedPullRequests(
        @PathParam("project") String projectKey,
        @PathParam("slug") String slug,
        @PathParam("pullRequestId") Long pullRequestId
    ) {
        PullRequest pullRequest = this.getPullRequest(projectKey, slug, pullRequestId); 
        if (pullRequest == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorRestResourceModel("Error Retrieving Links", 
                    String.format(
                        "Pull request #%d not found for repo %s/%s.",
                        pullRequestId, projectKey, slug
                    )
                ))
                .build();
        }

        return Response.status(Response.Status.OK)
            .entity(new LinkedPullRequest(
                pullRequest, this.pluginSettings, this.pullRequestService
            ))
            .build();
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON})
    public Response createLinkedPullRequest(
        @PathParam("project") String projectKey,
        @PathParam("slug") String slug,
        @PathParam("pullRequestId") Long pullRequestId,
        @RequestBody LinkedPullRequestRestResourceModel toLinkedPullRequest
    ) {
        PullRequest pullRequest = this.getPullRequest(projectKey, slug, pullRequestId); 
        if (pullRequest == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorRestResourceModel("Error Creating Link", 
                    String.format(
                        "Pull request #%d not found for repo %s/%s.",
                        pullRequestId, projectKey, slug
                    )
                ))
                .build();
        }

        PullRequest toPullRequest = this.pullRequestService.getById(
            toLinkedPullRequest.repositoryId, toLinkedPullRequest.pullRequestId       
        );
        if (toPullRequest == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorRestResourceModel("Error Creating Link", 
                    String.format(
                        "Target pull request #%d not found for repo with ID %d.",
                        toLinkedPullRequest.pullRequestId,
                        toLinkedPullRequest.repositoryId
                    )
                ))
                .build();
        }

        LinkedPullRequest linkedPullRequest = new LinkedPullRequest(
            pullRequest, this.pluginSettings, this.pullRequestService
        );
        try {
            linkedPullRequest.createLink(toPullRequest, PullRequestLink.Direction.TO);
        } catch (Exception e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorRestResourceModel("Error Creating Link", e.getMessage()))
                .build();
        }
        return Response.status(Response.Status.CREATED).entity(linkedPullRequest).build();
    }

    @DELETE
    @Path("/linked/{id}")
    public Response removeLinkedPullRequest(
        @PathParam("project") String projectKey,
        @PathParam("slug") String slug,
        @PathParam("pullRequestId") Long pullRequestId,
        @PathParam("id") UUID id,
        @QueryParam("direction") String directionParam 
    ) {
        directionParam = directionParam.toLowerCase();
        PullRequestLink.Direction direction;
        if (directionParam.equals("to")) {
            direction = PullRequestLink.Direction.TO;
        } else if (directionParam.equals("from")) {
            direction = PullRequestLink.Direction.FROM;
        } else if (directionParam.equals("bidirectional")) {
            direction = PullRequestLink.Direction.BIDIRECTIONAL;
        } else {
            return Response.status(422)
                .entity(new ErrorRestResourceModel("Error Deleting Link",
                    "Invalid direction parameter."            
                ))
                .build();
        }

        PullRequest pullRequest = this.getPullRequest(projectKey, slug, pullRequestId); 
        if (pullRequest == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorRestResourceModel("Error Deleting Link", 
                    String.format(
                        "Pull request #%d not found for repo %s/%s.",
                        pullRequestId, projectKey, slug
                    )
                ))
                .build();
        }

        LinkedPullRequest linkedPullRequest = new LinkedPullRequest(
            pullRequest, this.pluginSettings, this.pullRequestService
        );
        if (linkedPullRequest.removeLink(id, direction) == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorRestResourceModel("Error Deleting Link", 
                    "No link with the given ID exists."
                ))
                .build();
        }
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    private PullRequest getPullRequest(
        String projectKey, String slug, Long pullRequestId
    ) {
        Repository repo = this.repositoryService.getBySlug(projectKey, slug);
        if (repo == null) {
            return null;
        }

        PullRequest pullRequest = this.pullRequestService.getById(
            repo.getId(), pullRequestId
        );
        if (pullRequest == null) {
            return null;
        }
        return pullRequest;
    }

}
