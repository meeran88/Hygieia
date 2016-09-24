(function () {
	'use strict';

	angular
	.module(HygieiaConfig.module)
	.controller('projectVersionViewController', projectVersionViewController);

	projectVersionViewController.$inject = ['$scope', 'DashStatus', 'projectData', 'DisplayState', '$q', '$modal'];
	function projectVersionViewController($scope, DashStatus, projectData, DisplayState, $q, $modal) {
		/*jshint validthis:true */
		var ctrl = this;

		// public variables
		ctrl.statuses = DashStatus;
        ctrl.projectData = projectData;
		ctrl.load = load;
		ctrl.jiraDashboard = {};

		ctrl.showDetail = showDetail;
		ctrl.title = "";

		function load() {
			var deferred = $q.defer();
			console.log("In Load...");
			projectData.details($scope.widgetConfig.componentId).then(function(data){

				processProjectData(data);
				deferred.resolve(data.lastUpdated);
			
				console.log("Done Load");

			});
			return deferred.promise;
		}
		function processProjectData(data) {
		  console.log("In process project data");
		  $scope.subtitle = data.result.summary.projectName;
	      ctrl.jiraDashboard = {
	      		"issueSummary" :data.result.summary,
	      		"issues":data.result.issues
	  	  }
	      console.log(ctrl.jiraDashboard.issueSummary);

	      //Data preparation for chart
	      //{"summary":{"inprogressCount":9,"doneCount":126,"pendingCount":47,"projectName":"API","versionName":"Chase Pay 1.0","issueCount":182},
	      var chartData = {
	      	labels: ['Done','To Do','In Progress'],
	      	series: [data.result.summary.doneCount,data.result.summary.pendingCount,data.result.summary.inprogressCount],
	      	colors:['green','orange','red']
	      }
	      ctrl.pieOptions = {
            donut: true,
            donutWidth: 30,
            startAngle: 270,
            total: 360,
            labelOffset:20,
            width:'350px',
            height:'200px',
            showLabel: true
        };
	      ctrl.jiraDashboard.chartData = chartData;
	    }

	    function showDetail(jiraDashboard){
	    	$modal.open({
				controller: 'projectVersionViewDetailController',
				controllerAs: 'detail',
				templateUrl: 'components/widgets/project/detail.html',
				size: 'lg',
				resolve: {
					jiraDashboard: function() {
						return jiraDashboard;
					},
					collectorName: function () {
						
						return $scope.dashboard.application.components[0].collectorItems.Jiraproject[0].collector.name;
					},
                    collector: function () {
                        return $scope.dashboard.application.components[0].collectorItems.Jiraproject[0].collector;
                    }
				}
			});
	    }

	}


})();