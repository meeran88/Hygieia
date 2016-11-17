(function () {
    'use strict';

    angular
        .module(HygieiaConfig.module)
        .controller('deployConfigController', deployConfigController);

    deployConfigController.$inject = ['modalData', 'collectorData','$modalInstance'];
    function deployConfigController(modalData, collectorData, $modalInstance) {
        /*jshint validthis:true */
        var ctrl = this;
        var widgetConfig = modalData.widgetConfig;
        ctrl.deployJobs = [];
        ctrl.jobDropdownDisabled = true;
        ctrl.jobDropdownPlaceholder = 'Loading...';
        ctrl.submitted = false;

        // public methods
        ctrl.submit = submit;

        collectorData.itemsByType('deployment').then(processResponse);

        function processResponse(data) {
            var worker = {
                getDeploys: getDeploys
            };

            function getDeploys(data, currentCollectorId, cb) {
                var selectedIndex = null;

                var deploys = _(data).map(function(deploy, idx) {
                    if(deploy.id == currentCollectorId) {
                        selectedIndex = idx;
                    }
                    return {
                        value: deploy.id,
                        name: deploy.options.applicationName
                    };
                }).value();

                cb({
                    deploys: deploys,
                    selectedIndex: selectedIndex
                });
            }

            var deployCollector = modalData.dashboard.application.components[0].collectorItems.Deployment;
            var deployCollectorId = deployCollector ? deployCollector[0].id : null;
            worker.getDeploys(data, deployCollectorId, getDeploysCallback);
        }

        function getDeploysCallback(data) {
                 ctrl.jobDropdownDisabled = false;
                ctrl.jobDropdownPlaceholder = 'Select your application';
                ctrl.deployJobs = data.deploys;

                if(data.selectedIndex !== null) {
                    ctrl.deployJob = data.deploys[data.selectedIndex];
                }
        }


        function submit(valid) {
            ctrl.submitted = true;
            if (valid) {
                var form = document.configForm;
                var postObj = {
                    name: 'deploy',
                    options: {
                        id: widgetConfig.options.id
                    },
                    componentId: modalData.dashboard.application.components[0].id,
                    collectorItemId: form.deployJob.value
                };

                $modalInstance.close(postObj);
            }
        }
    }
})();
