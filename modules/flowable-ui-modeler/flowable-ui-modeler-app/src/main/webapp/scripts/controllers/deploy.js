angular.module('flowableModeler')
  .controller('LoginController', ['$scope', '$http', 'Base64', '$log', '$window', '$modal', 
                              function ($scope, $http, Base64, $log, $window, $modal) {
    var vm = this;
    vm.loginDetail = {
      "name" : " ",
      "password" : ""
    };
    
    vm.deployOptions = FLOWABLE.CONFIG.deployUrls || [];
    
    vm.doAction = doAction;
    vm.cancel = cancel;
    vm.capitalizedAction = capitalizedAction;
    
    var processId = $scope.model.process.id;
    var processKey = $scope.model.process.key;

    function doAction() {
        switch($scope.action) {
            case 'deploy':
                withLogin(_deploy);
                break;
            case 'activate':
                withLogin(_activate);
                break;
            case 'suspend':
                withLogin(_suspend);
        }
    }

    //var deployUrl = FLOWABLE.CONFIG.deployUrl ? FLOWABLE.CONFIG.deployUrl : "http://localhost:8080/runtime/workflow/deploy";
    function withLogin(actionFn) {
      if (vm.loginDetail.name && vm.loginDetail.password && vm.deployUrl) {
        vm.actionInProgress = true;
        vm.errorMsg = "Deployment in progress!";
        var authdata = Base64.encode(vm.loginDetail.name + ':' + vm.loginDetail.password);

        actionFn(authdata);

      } else {
        vm.errorMsg = "Please give username, password, environment for " + $scope.action+ "!";
      }
    }

    function _deploy(authdata) {
        $http.get("app/rest/models/" + processId + "/exportForDeploy").then(function(response) {
            _runtimeApiCall(authdata, "Deploy", vm.deployUrl, response.data)
        }, function(errorResposne) {
            vm.actionInProgress = false;
            vm.errorMsg = "Cannot generate bpmn file for deployment! Please ask system administrator for help!";
        });
    }

    function _suspend(authdata) {
        var url = vm.deployUrl.replace("deploy", "suspend") + "/"+ processKey;
        _runtimeApiCall(authdata, "Suspend", url, {})
    }

    function _activate(authdata) {
        var url = vm.deployUrl.replace("deploy", "activate") + "/"+ processKey;
        _runtimeApiCall(authdata, "activate", url, {})
    }

    function _runtimeApiCall(authdata, actionName, actionUrl, data) {
        $http.defaults.headers.common['Authorization'] = 'Basic ' + authdata;
        $http({
            method : "POST",
            url : actionUrl,
            data : data
        }).then(function(deployResponse) {
            vm.actionInProgress = false;
            $window.alert(actionName + " successfully!");
            $scope.$hide();
        }, function(deployResponse) {
            vm.actionInProgress = false;
            $log.debug(actionName + ' error!', deployResponse);
            if (deployResponse.status == "401") {
                vm.errorMsg = "Wrong username or password! try again";
            } else if (deployResponse.status == "400") {
                vm.errorMsg = deployResponse.data.error;
            } else {
                vm.errorMsg = "Something went wrong, please ask system administrator for help!";
            }
        });
        delete $http.defaults.headers.common['Authorization'];
    }

    function cancel() {
      $scope.$hide();
    }

    function capitalizedAction() {
        var action = $scope.action;
        return action.charAt(0).toUpperCase() + action.substr(1);
    }

}]);