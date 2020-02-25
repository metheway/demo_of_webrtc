'use strict'

var http = require('http');
var https = require('https');
var fs = require('fs');

var express = require('express');
var serveIndex = require('serve-index');
var _ = require('underscore');

//socket.io
var socketIo = require('socket.io');

var log4js = require('log4js');

log4js.configure({
    appenders: {
        file: {
            type: 'file',
            filename: 'app.log',
            layout: {
                type: 'pattern',
                pattern: '%r %p - %m',
            }
        }
    },
    categories: {
       default: {
          appenders: ['file'],
          level: 'debug'
       }
    }
});

var logger = log4js.getLogger();

var app = express();
app.use(serveIndex('./public'));
app.use(express.static('./public'));

//http server
var http_server = http.createServer(app);
http_server.listen(8081, '0.0.0.0');

var sockio = socketIo.listen(http_server);
var socketMap = new Map();

// 应该加锁
console.log("conn listening");
sockio.sockets.on('connection', (socket)=>{
	
	console.log("connected");
	// 连接以后就监听交换信令／candidates的信息

	socket.on('join', (roomId) => {
		socketMap.set(socket.id, socket);

		socket.join(roomId);
		var myRoom = sockio.sockets.adapter.rooms[roomId];
		console.log("myRoom:", myRoom);
		var users = Object.keys(myRoom.sockets).length;
		console.log("join receive, users:", users);
		logger.log('the number of user in room is: ' + users);
	
		if(users < 4) {
			socket.emit('joined', roomId, socket.id);	
			console.log(socket.id);
			// 向房间里其他人告知有人进入，并且将id号发过去
			// 第三人C加入，向A，B推送otherjoin
			socket.to(roomId).emit('otherjoin', roomId, socket.id);
			users++;
		}else {
			// 见这个用户移除并告知已经满人了,
			socket.leave(roomId);
			socket.emit('full', roomId, socket.id);	
		}
	});

	socket.on("SdpInfo", function(jsonObj) {
		console.log("for test:" + jsonObj.roomId);
		var roomId = jsonObj.roomId;
		// 如果发过来的是SdpInfo，那么直接给转发给相应的人
		// socket.to(roomId).emit('SdpInfo', jsonObj, socket.id);
		// sockio.sockets[toSocket].emit('SdpInfo', jsonObj, socket.id);
		// var toSocket = _.findWhere(sockio.sockets, {id : toSocketId});
		// toSocket.emit('SdpInfo', jsonObj, socket.id);
		
		var toSocketId = jsonObj.to;
		console.log(toSocketId);
		var toSocket = socketMap.get(toSocketId);
		// 收到SdpInfo，给这个user发送自己的sdp信息及id号
		toSocket.emit('SdpInfo', jsonObj, socket.id);

		// sockio.sockets[toSocketId].emit('SdpInfo', jsonObj, socket.id);
		// sockio.sockets.socket(toSocketId).emit('SdpInf', jsonObj, socket.id);
		// if (sockio.sockets.connected[toSocketId]) {
			// sockio.sockets.connected[toSocketId].emit('SdpInfo', jsonObj, socket.id);
		// }
	});

	socket.on("IceInfo", function(jsonObj) {
		var roomId = jsonObj.roomId;
		console.log("IceInfo", jsonObj);
		// 这里收到谁的IceInfo，要发出这个用户的id，向除了自己外的所有用户
		socket.to(roomId).emit("IceInfo", jsonObj, socket.id);
	});

	socket.on('leave', (roomId)=> {
		var myRoom = sockio.sockets.adapter.rooms[roomId];
		if (null != myRoom) {			
			var users = Object.keys(myRoom.sockets).length;
			users = users - 1;
			logger.log('the number of user in room is: ' + (users-1));
	
			socket.leave(roomId);
			socket.emit('left', roomId, socket.id);	
			socket.to(roomId).emit('bye', roomId, socket.id);	
		}
	 	//socket.to(room).emit('joined', room, socket.id);//除自己之外
		//io.in(room).emit('joined', room, socket.id)//房间内所有人
	 	//socket.broadcast.emit('joined', room, socket.id);//除自己，全部站点	
	});
});