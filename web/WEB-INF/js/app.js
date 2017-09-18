var contextPath;
var roomName;
var subscribeTo;
var nickname;
var sessionId = null;
var webSocket;


/**
 * Perform validation and send request to server
 * to join the entered room.
 */
function signIn() {
    var error = false;

    nickname = document.getElementById('nickname').value;
    if (nickname.trim() === 0) {
        $('#nickname').css({"border": '#FF0000 1px solid'});
        error = true;
    } else {
        $('#nickname').css({"border": '#000000 1px solid'});
    }

    roomName = document.getElementById('roomname').value;
    if (roomName === "") {
        $('#roomname').css({"border": '#FF0000 1px solid'});
        error = true;
    } else if (roomName === 'other') {
        roomName = document.getElementById('newroomname').value;
        if (roomName.trim().length === 0) {
            $('#newroomname').css({"border": '#FF0000 1px solid'});
            error = true;
        } else {
            $('#newroomname').css({"border": '#000000 1px solid'});
        }
    } else {
        $('#roomname').css({"border": 'initial'});
    }

    if (!error) {
        subscribeTo = "/" + roomName + "/messages";
        $('#nickname').css("border: #000000 1px solid;");
        $('#roomname').css("border: #000000 1px solid;");
        $('#newroomname').css("border: #000000 1px solid;");
        connect();
    }
}


/**
 * Create connection to the room and subscribe for messages.
 */
function connect() {

    webSocket = new WebSocket("ws://localhost:8080/app/chat");

    webSocket.onopen = function (event) {
        if (event === undefined) {
            return;
        }

        if (webSocket.readyState === 1) {
            var requestToSend = JSON.stringify({
                action: "joinRoom",
                roomName: roomName,
                nickName: nickname,
                sessionId: sessionId,
                message: null
            });
            console.log("SENDING: " + requestToSend);
            webSocket.send(requestToSend);
        }
    };

    webSocket.onclose = function () {
        console.log("SERVER CLOSED CONNECTION ...");
        pageLoad(contextPath);
    };

    webSocket.onmessage = function (msg) {

        console.log("GOT ... " + msg.data + " ||| type ... " + typeof msg.data);
        var response = JSON.parse(msg.data);
        // Handling based on Response Code

        // 200 - JOINED SUCCESSFULLY
        if (response.responseCode === 200) {
            sessionId = response.sessionId;
            console.log("CONNECTED : SESSION ... " + sessionId);
            onConnected();

            // 201 - JOIN FAILED - Todo
        } else if (response.responseCode === 201) {
            alert("Cannot join the specified room.");

            // 202 - Someone Joined
        } else if (response.responseCode === 202) {
            showMessage(response);

            // 203 - Left Successfully 'Viewer left room'
        } else if (response.responseCode === 203) {
            pageLoad(contextPath);

            // 204 - Left failed - Todo
        } else if (response.responseCode === 204) {
            alert("Cannot leave room!");

            // 205 - Someone has left the room
        } else if (response.responseCode === 205) {
            showMessage(response);

            // 210 - Message Received 'by server'
        } else if (response.responseCode === 210) {
            showMessage(response);

            // 211 - Message Forwarded 'Message available in room'
        } else if (response.responseCode === 211) {
            showMessage(response);

        } else {
            console.log("UNDEFINED -- GOT: ... " + msg.data);
        }

    };

    webSocket.onerror = function (event) {
        console.log("ERROR: " + event.data);
    }

}


/**
 * Enables the chatbox and disables signin box
 */
function onConnected() {
    $('#signin').hide();
    $('#chatbox').show();
    $('#inroomname').text(roomName);
}


/**
 * Validate and send message to the room.
 */
function sendMessage() {

    var error = false;
    var msg = document.getElementById('message').value;

    if (msg.trim().length === 0) {
        $('#message').css({"border": '#FF0000 1px solid'});
        error = true;
    } else {
        $('#message').css({"border": '#000000 1px solid'});
    }

    if (!error) {
        var date = new Date();
        var msgToSend = JSON.stringify({
            action: "sendMessage",
            roomName: roomName,
            sessionId: sessionId,
            message: {
                from: nickname,
                message: msg,
                time: date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds()
            }
        });
        console.log("SENDING: " + msgToSend);
        webSocket.send(msgToSend);
    }

}


/**
 * Displays the obtained message
 * @param jsonResponse json object containing message to display
 */
function showMessage(jsonResponse) {
    var msg = jsonResponse.message;
    var style = '';

    // If someone joined the room
    if (jsonResponse.responseCode === 202) {
        style += 'background-color: #f9ff93;';
        // If someone left the room
    } else if (jsonResponse.responseCode === 205) {
        style += 'background-color: #ff885e;'
    }


    if (msg.from === roomName) {
        $('#messagebox').append("<span class='roomMsg' style='" + style + "'>" + msg.from + ": " + msg.message + " " + msg.time + "</span><br>");
    } else {
        var from = msg.from;
        var fromContainer = 'container';
        var fromClass = 'from';
        var fromMsgClass = 'msg';
        if (from === nickname) {
            from = 'You';
            fromClass = 'my-from';
            fromMsgClass = 'my-msg';
            fromContainer = 'my-container';
        }
        $('#messagebox').append('<div class="' + fromContainer + '"><span class="' + fromClass + '">' + from + '</span><br><div class="' + fromMsgClass + '">' + msg.message + '</div><span class="time">' + msg.time + '</span><br></div>');
    }
}


/**
 * Enables the text input field for entering new room's name
 * depending on value passed
 * @param value if 'other' selected enable input
 */
function showInputIfOther(value) {
    if (value === "other") {
        $('#inputnewroom').show();
    } else {
        $('#inputnewroom').hide();
    }
}


/**
 * Initialize components and sets contextPath
 * @param path contextPath from for the app
 */
function pageLoad(path) {
    contextPath = path;
    $('#signin').show();
    $('#inputnewroom').hide();
    $('#chatbox').hide();
    $('#messagebox').empty();
    $('#inroomname').empty();
    $('#nickname').val('');
    $('#roomname')[0].selectedIndex = 0;
    $('#newroomname').val('');
    $('#message').val('');
}


/**
 * Disconnect the subscriber and reinitialize the components
 */
function disconnect() {
    if (webSocket !== undefined) {
        var exitRequest = JSON.stringify({
            action: "leaveRoom",
            roomName: roomName,
            nickName: nickname,
            sessionId: sessionId,
            message: null
        });
        console.log("SENDING: " + exitRequest);
        webSocket.send(exitRequest);
    }
}