'use strict';

angular.module('codebrag.commits.comments')
    .factory('Comments', function ($resource) {
        return $resource('rest/commits/:id/comments', {id: '@commitId'}, {'query': {method: 'GET', isArray: false}});
    })

    .factory('Likes', function ($resource) {
        return $resource('rest/commits/:id/likes', {id: '@commitId'}, {'query': {method: 'GET', isArray: false}});
    });