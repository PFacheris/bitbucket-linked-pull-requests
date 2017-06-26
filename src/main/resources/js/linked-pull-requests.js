(function($) {
    // Set up our namespace
    window.Facheris = window.Facheris || {};
    Facheris.LINKED_PULL_REQUESTS = Facheris.LINKED_PULL_REQUESTS || {};

    // Deal with the nitty-gritty of localStorage
    function storageKey(pullRequestJson) {
        var repo = pullRequestJson.toRef.repository;
        var proj = repo.project;
        return 'facheris.linkedpullrequests.pullrequest.' + proj.key + '/' + repo.slug + '/' + pullRequestJson.id;
    }
    var storage = window.localStorage ? {
        getReferencesToPullRequests : function(pullRequestJson) {
            var item = localStorage.getItem(storageKey(pullRequestJson));
            try {
                return JSON.parse(item) || [];
            } catch(e) {
                return [];
            }
        },
        putReferencesToPullRequests : function(pullRequestJson, referencesToPullRequests) {
            localStorage.setItem(storageKey(pullRequestJson), JSON.stringify(referencesToPullRequests));
        }
    } : {
        getReferencesToPullRequests : function() {},
        putReferencesToPullRequests : function() {}
    };

    /**
     * The client-condition function takes in the context
     * before it is transformed by the client-context-provider.
     * If it returns a truthy value, the panel will be displayed.
     */
    function hasAnyReferencesToPullRequests(context) {
        var referencesToPullRequests = storage.getReferencesToPullRequests(context['pullRequest']);
        return referencesToPullRequests.length;
    }

    /**
     * The client-context-provider function takes in context and transforms
     * it to match the shape our template requires.
     */
    function getReferencesToPullRequestsStats(context) {
        var referencesToPullRequests = storage.getReferencesToPullRequests(context['pullRequest']);
        return {
            referencesToPullRequests: referencesToPullRequests 
        };
    }

    function addReferenceToPullRequest(pullRequestJson, project, repo, number) {
        var referencesToPullRequests = storage.getReferencesToPullRequests(pullRequestJson);
        referencesToPullRequests.push({
            id : new Date().getTime() + ":" + Math.random(),
            project: project,
            repo: repo,
            number: number,
            link: require('bitbucket/util/navbuilder').project(project).repo(repo).pullRequest(number).build()
        });
        storage.putReferencesToPullRequests(pullRequestJson, referencesToPullRequests);
    }

    function removeReferenceToPullRequest(pullRequestJson, referenceToPullRequestId) {
        var referencesToPullRequests = storage.getReferencesToPullRequests(pullRequestJson).filter(function(referenceToPullRequest) {
            return referenceToPullRequest.id != referenceToPullRequestId;
        });
        storage.putReferencesToPullRequests(pullRequestJson, referencesToPullRequests);
    }


    /* Expose the client-condition function */
    Facheris.LINKED_PULL_REQUESTS._pullRequestIsOpen = function(context) {
        var pr = context['pullRequest'];
        return pr.state === 'OPEN';
    };

    /* Expose the client-context-provider function */
    Facheris.LINKED_PULL_REQUESTS.getReferencesToPullRequestsStats = getReferencesToPullRequestsStats;

    Facheris.LINKED_PULL_REQUESTS.addReferenceToPullRequest = addReferenceToPullRequest;

    Facheris.LINKED_PULL_REQUESTS.removeReferenceToPullRequest = removeReferenceToPullRequest;

    function showDialog() {
        var dialog = showDialog._dialog;
        if (!dialog) {
            dialog = showDialog._dialog = new AJS.Dialog()
                .addHeader("Link New Pull Request")
                .addPanel("Link New Pull Request")
                .addCancel("Close", function() {
                    dialog.hide();
                });
        }

        require([
            'bitbucket/util/navbuilder',
            'bitbucket/util/server'
        ], function(nav, server){
            server.rest({
                type : 'GET',
                url : nav.rest().addPathComponents('projects').build()
            }).done(function(response) {
                var projectOptions = _.chain(response['values']
                ).filter(function(project){
                    return project['type'] == 'NORMAL';
                }).map(function(project) {
                    return {
                        'text': project['name'],
                        'value': project['key']
                    };
                }).value()
                dialog.getCurrentPanel().body.html(
                    me.facheris.referenceToPullRequestModal({
                        projectOptions: projectOptions
                    })
                );
                dialog.show().updateHeight();
                // Render project select and populate repo select with appropriate
                // options based on initial selection
                var $dialog = dialog.getCurrentPanel().body
                $dialog.find('#project').auiSelect2();
                renderRepoSelect();
            });
        });
    }

    function renderRepoSelect() {
        var $form = $('#reference-to-pull-request-create-form');
        var projectKey = $form.find('#project').val();
        require([
            'bitbucket/util/navbuilder',
            'bitbucket/util/server'
        ], function(nav, server){
            server.rest({
                type : 'GET',
                url : nav.rest().project(projectKey).allRepos().build()
            }).done(function(response) {
                var $repoField = $form.find('#repo');
                var data = _.map(response['values'], function(repo) {
                    return {
                        id: repo['slug'],
                        text: repo['name']
                    }
                });
                $repoField.auiSelect2({
                    'data': data
                })
                $repoField.prop('disabled', false);
            });
        });
    }

    function renderReferencesToPullRequestsLink() {
        var pr = require('bitbucket/internal/model/page-state').getPullRequest();
        var newStats = Facheris.LINKED_PULL_REQUESTS.getReferencesToPullRequestsStats({ pullRequest : pr.toJSON() });
        $('.mycompany-todos-link').replaceWith(me.facheris.prOverviewPanel(newStats));
    }

    /* use a live event to handle the link being clicked. */
    $(document).on('click', '.mycompany-todos-link', function(e) {
        e.preventDefault();
        showDialog();
    });

    $(document).on('change', '#reference-to-pull-request-create-form #project', function(e) {
        e.preventDefault();
        renderRepoSelect();
    });

    $(document).on('submit', "#reference-to-pull-request-create-form", function(e) {
        e.preventDefault();
        var pr = require('bitbucket/internal/model/page-state').getPullRequest();

        var $project = AJS.$(this).find("#project");
        var $repo = AJS.$(this).find("#repo");
        var $number = AJS.$(this).find("#number");
        var text = $project.val() + '/' + $repo.val() + '#' + $number.val();
        Facheris.LINKED_PULL_REQUESTS.addReferenceToPullRequest(
            pr.toJSON(),
            $project.val(),
            $repo.val(),
            $number.val()
        );
        renderReferencesToPullRequestsLink();
    });

    $(document).on('click', '.references-to-pull-requests-list .remove', function(e) {
        e.preventDefault();
        var referenceToPullRequestId = $(this).closest('li').attr('data-reference-to-pull-request-id');

        var prJSON = require('bitbucket/internal/model/page-state').getPullRequest().toJSON();

        Facheris.LINKED_PULL_REQUESTS.removeReferenceToPullRequest(prJSON, referenceToPullRequestId);

        renderReferencesToPullRequestsLink();
    })
}(AJS.$));
