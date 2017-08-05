(function($) {
    // Set up our namespace
    window.Facheris = window.Facheris || {};
    Facheris.LINKED_PULL_REQUESTS = Facheris.LINKED_PULL_REQUESTS || {};

    // Deal with the nitty-gritty of localStorage
    var storage = {
        getRepositoryId: function(project, repo) {
            var deferred = new $.Deferred();
            var url = require('bitbucket/util/navbuilder')
                .rest()
                .project(project)
                .repo(repo)
                .build();
            require('bitbucket/util/server').rest({
                url: url
            }).done(function(data) {
                deferred.resolve(data['id']);
            })
            .fail(function(data) {
                deferred.reject();
            });
            return deferred.promise();
        },
        getLinkedPullRequests : function() {
            var deferred = new $.Deferred();
            var url = require('bitbucket/util/navbuilder')
                .rest('linked-pull-requests')
                .currentPullRequest()
                .build();
            require('bitbucket/util/server').rest({
                url: url
            }).done(function(data) {
                var links = $.map(data['links'], function(link) {
                    link['link'] = require('bitbucket/util/navbuilder')
                        .project(link.project)
                        .repo(link.slug)
                        .pullRequest(link.pullRequestId)
                        .build();
                    return link;
                });
                deferred.resolve(links);
            })
            .fail(function(data) {
                deferred.reject(data);
            });
            return deferred.promise();
        },
        postLinkedPullRequest : function(linkedPullRequest) {
            var deferred = new $.Deferred();
            var url = require('bitbucket/util/navbuilder')
                .rest('linked-pull-requests')
                .currentPullRequest()
                .build();
            require('bitbucket/util/server').rest({
                url: url,
                method: 'POST',
                data: JSON.stringify(linkedPullRequest)
            }).done(function(data) {
                var links = $.map(data['links'], function(link) {
                    link['link'] = require('bitbucket/util/navbuilder')
                        .project(link.project)
                        .repo(link.slug)
                        .pullRequest(link.pullRequestId)
                        .build();
                    return link;
                });
                deferred.resolve(links);
            })
            .fail(function(data) {
                deferred.reject(data);
                return data;
            });
            return deferred.promise();
        },
        deleteLinkedPullRequest : function(id) {
            var deferred = new $.Deferred();
            var url = require('bitbucket/util/navbuilder')
                .rest('linked-pull-requests')
                .currentPullRequest()
                .addPathComponents('linked', id)
                .withParams({
                    'direction': 'bidirectional' 
                })
                .build();
            require('bitbucket/util/server').rest({
                url: url,
                method: 'DELETE'
            }).done(function(data) {
                deferred.resolve(data);
            })
            .fail(function(data) {
                deferred.reject(data);
            });
            return deferred.promise();
        }
    };

    /**
     * The client-context-provider function takes in context and transforms
     * it to match the shape our template requires.
     */
    function getLinkedPullRequestsStats(context) {
        storage.getLinkedPullRequests()
            .done(function(linkedPullRequests) {
                $('#linked-pull-requests-panel-container').replaceWith(
                    me.facheris.prOverviewPanel({
                        linkedPullRequests: linkedPullRequests 
                    })
                );
            });
        return {
            linkedPullRequests: [],
            loading: true
        };
    }

    function addLinkedPullRequest(project, repo, number) {
        storage.getRepositoryId(project, repo).done(function(repositoryId) {
            linkedPullRequest = {
                repositoryId: repositoryId,
                pullRequestId: number
            };
            storage.postLinkedPullRequest(linkedPullRequest).done(function() {
                renderLinkedPullRequestsLink();
            });
        });
    }

    function removeLinkedPullRequest(linkedPullRequestId) {
        storage.deleteLinkedPullRequest(linkedPullRequestId).done(function() {
            renderLinkedPullRequestsLink();   
        });
    }


    /* Expose the client-condition function */
    Facheris.LINKED_PULL_REQUESTS._pullRequestIsOpen = function(context) {
        var pr = context['pullRequest'];
        return pr.state === 'OPEN';
    };

    /* Expose the client-context-provider function */
    Facheris.LINKED_PULL_REQUESTS.getLinkedPullRequestsStats = getLinkedPullRequestsStats;

    Facheris.LINKED_PULL_REQUESTS.addLinkedPullRequest = addLinkedPullRequest;

    Facheris.LINKED_PULL_REQUESTS.removeLinkedPullRequest = removeLinkedPullRequest;

    var get_pages = function(url, start, limit, callback, values) {
        values = typeof values !== 'undefined' ? values : []

        var params = {'limit': limit, 'start': start};
        require([
            'bitbucket/util/server'
        ], function(server){
            server.rest({
                type : 'GET',
                url : url,
                data: params
            }).done(function(response) {
                values = values.concat(response['values']);
                if (response['isLastPage']) {
                    callback(values);
                } else {
                    get_pages(url, response['nextPageStart'], response['limit'], callback, values);
                }
            });
        });
    };

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

        var render = function(projectOptions) {
            dialog.getCurrentPanel().body.html(
                    me.facheris.linkedPullRequestModal({
                        projectOptions: projectOptions
                    })
                );
                dialog.show().updateHeight();
                // Render project select and populate repo select with appropriate
                // options based on initial selection
                var $dialog = dialog.getCurrentPanel().body
                $dialog.find('#project').auiSelect2();
                renderRepoSelect();
        };

        // Make API request
        require([
            'bitbucket/util/navbuilder',
        ], function(nav){ 
            var url = nav.rest().addPathComponents('projects').build();
            get_pages(url, 0, 25, function(values) {
                var projectOptions = _.chain(values
                ).filter(function(project){
                    return project['type'] == 'NORMAL';
                }).map(function(project) {
                    return {
                        'text': project['name'],
                        'value': project['key']
                    };
                }).value();
                render(projectOptions);
            });
        });
    }

    function renderRepoSelect() {
        var $form = $('#linked-pull-request-create-form');
        var projectKey = $form.find('#project').val();
        require([
            'bitbucket/util/navbuilder',
        ], function(nav, server){
            var url = nav.rest().project(projectKey).allRepos().build();
            get_pages(url, 0, 25, function(values) {
                var $repoField = $form.find('#repo');
                var data = _.map(values, function(repo) {
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

    function renderLinkedPullRequestsLink() {
        Facheris.LINKED_PULL_REQUESTS.getLinkedPullRequestsStats();
    }

    /* use a live event to handle the link being clicked. */
    $(document).on('click', '.mycompany-todos-link', function(e) {
        e.preventDefault();
        showDialog();
    });

    $(document).on('change', '#linked-pull-request-create-form #project', function(e) {
        e.preventDefault();
        renderRepoSelect();
    });

    $(document).on('submit', "#linked-pull-request-create-form", function(e) {
        e.preventDefault();
        var pr = require('bitbucket/internal/model/page-state').getPullRequest();

        var $project = AJS.$(this).find("#project");
        var $repo = AJS.$(this).find("#repo");
        var $number = AJS.$(this).find("#number");
        var text = $project.val() + '/' + $repo.val() + '#' + $number.val();
        Facheris.LINKED_PULL_REQUESTS.addLinkedPullRequest(
            $project.val(),
            $repo.val(),
            $number.val()
        );
    });

    $(document).on('click', '.linked-pull-requests-list .aui-icon-close', function(e) {
        e.preventDefault();
        var linkedPullRequestId = $(this).closest('li').attr('data-linked-pull-request-id');

        Facheris.LINKED_PULL_REQUESTS.removeLinkedPullRequest(linkedPullRequestId);
    })
}(AJS.$));
