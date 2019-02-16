angular.module('ot.dialog.project.packageIds', [
    'ot.service.core',
    'ot.service.form'
])
    .controller('otDialogProjectPackageIds', function ($scope, $modalInstance, $http, config, ot, otFormService) {
        // Inject the configuration into the scope
        $scope.config = config;
        $scope.packageIds = config.project.packageIds;
        // Cancelling the dialog
        $scope.cancel = () => {
            $modalInstance.dismiss('cancel');
        };
        // Submitting the dialog
        $scope.submit = isValid => {
            if (isValid) {
                otFormService.submitDialog(
                    config.submit,
                    $scope.data,
                    $modalInstance,
                    $scope
                );
            }
        };
    })
;