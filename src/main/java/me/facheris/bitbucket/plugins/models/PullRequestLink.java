package me.facheris.bitbucket.plugins.models;

import java.util.UUID;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.pull.PullRequestState;

import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.annotation.*;

/*
 * Represents a unique link from an origin PullRequest to a target PullRequest
 * and the direction of that link, the creator is responsible for correctly
 * assigning link direction.
 */
@XmlAccessorType(XmlAccessType.NONE)
public class PullRequestLink {
    public enum Direction {
        TO("to"), FROM("from"), BIDIRECTIONAL("bidirectional");

        private final String name;

        private Direction(String name) { 
            this.name = name; 
        } 
        
        @Override 
        public String toString(){ 
            return name; 
        } 
    } 

    @XmlElementRef(name="id")
    public UUID id;
    public PullRequest target;
    public Direction direction;

    public PullRequestLink(
        UUID id, PullRequest target, Direction direction
    ) {
        this.id = id;
        this.target = target;
        this.direction = direction;
    }

    public PullRequestLink(
        PullRequest target, Direction direction
    ) {
        this.id = UUID.randomUUID();
        this.target = target;
        this.direction = direction;
    }

    public int getRepositoryId() {
        return this.target.getToRef().getRepository().getId();
    }

    @XmlElementRef(name="slug")
    public String getSlug() {
        return this.target.getToRef().getRepository().getSlug();
    }

    @XmlElementRef(name="project")
    public String getProjectKey() {
        return this.target.getToRef().getRepository().getProject().getKey();
    }

    @XmlElementRef(name="pullRequestId")
    public long getPullRequestId() {
        return this.target.getId();
    }

    @XmlElementRef(name="direction")
    public String getDirection() {
        return this.direction.toString();
    }

    @XmlElementRef(name="state")
    public String getState() {
        return this.target.getState().toString();
    }

    public String serialize() {
        JSONObject json = new JSONObject();
        json.put("id", this.id);
        json.put("repositoryId", this.getRepositoryId());
        json.put("pullRequestId", this.getPullRequestId());
        json.put("direction", this.direction);
        return json.toString();
    }

    /*
     * Factory for generating PullRequestLinks from a serialized string,
     * returns null if the PullRequest target no longer exists.
     */
    public static PullRequestLink deserialize(
        PullRequestService pullRequestService,
        String str
    ) {
        JSONObject json;
        UUID id;
        int repositoryId;
        long pullRequestId;
        PullRequestLink.Direction direction;
        try {
            json = new JSONObject(str);
            id = UUID.fromString(json.getString("id"));
            repositoryId = json.getInt("repositoryId");
            pullRequestId = json.getLong("pullRequestId");
            direction = json.getEnum(PullRequestLink.Direction.class, "direction");
        } catch (JSONException e) {
            return null;
        }

        PullRequest target = pullRequestService.getById(repositoryId, pullRequestId); 
        if (target == null) {
            return null;
        }
        return new PullRequestLink(id, target, direction);
    }
}
