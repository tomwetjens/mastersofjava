var stompClientControl = null;
	function init() {
		connectTestFeedback();
		connectCompileFeedback();
		connectControl();
		connectStop();
	}
	
	function connectTestFeedback() {

		var socket = new SockJS('/submit');
		var stompTestFeedbackClient = Stomp.over(socket);
		stompTestFeedbackClient.debug = null;
		stompTestFeedbackClient.connect({}, function(frame) {
			console.log('Connected feedback');
			stompTestFeedbackClient.subscribe('/user/queue/feedback', function(messageOutput) {
				console.log("test feedback");
				var message = JSON.parse(messageOutput.body);
				if (!message.submit) {
					var response = document.getElementById(message.test);
					response.innerHTML = '<pre class="test-output">' + message.text + '</pre>';
					if (message.success) {
						$('#' + message.test + '-li > a').css("color", "green");	
					} else {
						$('#' + message.test + '-li > a').css("color", "red");
					}
				}				
			});
		});
	}

	function connectCompileFeedback() {

		var socket = new SockJS('/submit');
		var stompCompileFeedbacClient = Stomp.over(socket);
		stompCompileFeedbacClient.debug = null;
		stompCompileFeedbacClient.connect({}, function(frame) {
			console.log('Connected compilefeedback');
			stompCompileFeedbacClient.subscribe('/user/queue/compilefeedback', function(messageOutput) {
				console.log("compilefeedback");
				var message = JSON.parse(messageOutput.body);
				var response = document.getElementById("outputarea");
				response.innerHTML = "<pre>" + message.text + "</pre>";
				if (message.success) {
					$('#outputarea-li > a').css("color", "green");	
				} else {
					$('#outputarea-li > a').css("color", "red");
				}
				
			});

		});
	}

	function connectControl() {
		var socket = new SockJS('/control');
		stompClientControl = Stomp.over(socket);
		stompClientControl.debug = null;
		stompClientControl.connect({}, function(frame) {
			console.log('Connected to /control/queue/start');
			stompClientControl.subscribe('/queue/start', function(messageOutput) {
				console.log("/queue/start")
				window.location.reload();
			});

		});
	}
	
	function connectStop() {
		var socket = new SockJS('/control');
		var stompClientStop = Stomp.over(socket);
		stompClientStop.debug = null;
		stompClientStop.connect({}, function(frame) {
			console.log('Connected to /control/queue/stop');
			stompClientStop.subscribe('/queue/stop', function(taskTimeMessage) {
				var message = JSON.parse(taskTimeMessage.body);
				console.log("/queue/stop")
				disable();
			});

		});
	}
	
	function compile() {
		stompClientControl.send("/app/submit/compile", {}, JSON.stringify({
			'team' : 'team1',
			'source' :  getContent()
		}));
	}

	function test() { 
		cleartests();
		var tests = $("input:checkbox:checked").map(function(){
		      return $(this).val();
		    }).get();
		
		stompClientControl.send("/app/submit/test", {}, JSON.stringify({
			'team' : 'team1',
			'source' : getContent(),
			'tests' : tests
		}));
	}

	function cleartests() {
		var curTab = $('.ui-state-active');
		$('.ui-tabs-anchor').css("color", "black");	
		console.log($('#outputarea > pre'));
		$('.test-output').replaceWith('');
		$('#outputarea > pre').replaceWith('<pre></pre>');
	}	
	
	function disable() {
		// make readonly
		for (i = 0; i < filesArray.length; i++) {
			if (filesArray[i] != null) {
				console.log(filesArray[i]);
				filesArray[i].cmEditor.setOption("readOnly", true);
				console.log('#' + filesArray[i].name + '-tab');
				console.log($('#' + filesArray[i].name + '-tab .cm-s-default'));
				$('#' + filesArray[i].name + '-tab .cm-s-default').css( "background-color", "grey" );	
			}
		}
		// disable buttons
		$('#compile').attr('disabled','disabled');
		$('#test').attr('disabled','disabled');
		$('#submit').attr('disabled','disabled');
	}
	
    function getContent() {
		var editables = [];
		for(let i = 0; i < filesArray.length; i++){
			if (filesArray[i] != null && !filesArray[i].readonly &&  filesArray[i].fileType === 'EDIT') { 
				console.log('in');
				var file = {filename: filesArray[i].filename, content: filesArray[i].cmEditor.getValue()}
				editables.push(file);				
			}
		}
		
		return editables;
    }  
	function submit() {
		disable();
		stompClientControl.send("/app/submit/submit", {}, JSON.stringify({
			'team' : 'team1',
			'source' : getContent()
		}));
	}