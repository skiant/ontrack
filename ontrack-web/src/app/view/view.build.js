angular.module('ot.view.build', [
    'ui.router',
    'ot.service.core',
    'ot.service.structure'
])
    .config(function ($stateProvider) {
        $stateProvider.state('build', {
            url: '/build/{buildId}',
            templateUrl: 'app/view/view.build.tpl.html',
            controller: 'BuildCtrl'
        });
    })
    .controller('BuildCtrl', function ($scope, $stateParams, $http, ot, otStructureService) {
        var view = ot.view();
        // Build's id
        var buildId = $stateParams.buildId;

        // Loads the build
        function loadBuild() {
            otStructureService.getBuild(buildId).then(function (build) {
                $scope.build = build;
                // View configuration
                view.title = "Build " + build.name;
                view.description = build.description;
                view.breadcrumbs = ot.branchBreadcrumbs(build.branch);
                // Loads the promotion runs
                loadPromotionRuns();
                // Loads the validation runs
                loadValidationRuns();
                // Commands
                view.commands = [
                    {
                        condition: function () {
                            return build._promote;
                        },
                        id: 'promote',
                        name: "Promote",
                        cls: 'ot-command-promote',
                        action: promote
                    },
                    {
                        condition: function () {
                            return build._validate;
                        },
                        id: 'validate',
                        name: "Validation run",
                        cls: 'ot-command-validate',
                        action: validate
                    },
                    ot.viewCloseCommand('/branch/' + build.branch.id)
                ];
            });
        }

        // Page initialisation
        loadBuild();

        // Loads the promotion runs
        function loadPromotionRuns() {
            ot.call($http.get($scope.build._lastPromotionRuns)).then(function (promotionRunCollection) {
                angular.forEach(promotionRunCollection.resources, function (promotionRun) {
                    promotionRun.image = promotionRun.promotionLevel.image;
                });
                $scope.promotionRunCollection = promotionRunCollection;
            });
        }

        // Loads the validation runs
        function loadValidationRuns() {
            ot.call($http.get($scope.build._validationStampRunViews)).then(function (validationStampRunViewCollection) {
                angular.forEach(validationStampRunViewCollection.resources, function (validationStampRunView) {
                    validationStampRunView.image = validationStampRunView.validationStamp.image;
                });
                $scope.validationStampRunViewCollection = validationStampRunViewCollection;
            });
        }

        // Promotion
        function promote() {
            otStructureService.create($scope.build._promote, 'Promotion for the build').then(loadPromotionRuns);
        }

        // Validation
        function validate() {
            otStructureService.create($scope.build._validate, 'Validation for the build').then(loadBuild);
        }
    })
;