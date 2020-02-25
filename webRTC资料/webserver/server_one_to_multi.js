'use strict'

var http = require('http');
// var http = require('net');
var https = require('https');
var fs = require('fs');

var express = require('express');
var serveIndex = require('serve-index');

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
http_server.listen(8080, '0.0.0.0');

var options = {
	key : fs.readFileSync('cert/1557605_www.learningrtc.cn.key'),
	cert: fs.readFileSync('cert/1557605_www.learningrtc.cn.pem')
}

//https server
var https_server = https.createServer(options, app);
https_server.listen(4430, '0.0.0.0');

//bind socket.io with https_server
// var io = socketIo.listen(https_server);
var sockio = socketIo.listen(http_server);
var livings_map = new Map();
// roomId , socket.id

// 应该加锁
console.log("conn listening");
sockio.sockets.on('connection', (socket)=>{
	
	console.log("connected");
	// 连接以后就监听交换信令／candidates的信息
	socket.on('message', (room, data)=>{
		sockio.in(room).emit('message', room, socket.id, data)//房间内所有人
	});

	socket.on('join', (roomId) => {
		socket.join(roomId);
		var myRoom = sockio.sockets.adapter.rooms[roomId];
		console.log("myRoom:", myRoom);
		var users = Object.keys(myRoom.sockets).length;
		console.log("join receive, users:", users);
		logger.log('the number of user in room is: ' + users);
	
		if(users < 10) {
			socket.emit('joined', roomId, socket.id);	
			users++;
			// 如果每次加入，都向博主发出连接请求
			if (livings_map.has(roomId)) {
				// 后加入的，如果正在直播，那么给直播客发起个lineup请求	
				// var hostSocket = livings_map.get(roomId);
				socket.emit('lineup', roomId, livings_map.get(roomId).id);
			}
			// 其他人开始交换SDP
		}else {
			// 见这个用户移除并告知已经满人了,
			socket.leave(roomId);
			socket.emit('full', roomId, socket.id);	
		}
	});

	socket.on("SdpInfo", function(jsonObj) {
		console.log("for test:" + jsonObj.roomId);
		var roomId = jsonObj.roomId;
		socket.to(roomId).emit('SdpInfo', jsonObj, socket.id);
		// 这里其实可以判断下，直播客发过来给直播主，直播主发过来给所有直播客
		if (livings_map.get(roomId) == socket.id) {
			// 如果是直播主，给别的直播客发
			socket.to(roomId).emit('SdpInfo', jsonObj, socket.id);
		} else {
			// 如果是直播客，给直播主一个发
			var hostSocket = livings_map.get(roomId);
			hostSocket.emit('SdpInfo', jsonObj, socket.id);
		}

		// 发送给播主,如果有人正在主持，那么发送给播主
		// if (livings_map.has(roomId)) {
		// 	var hostSocket = livings_map.get(roomId);	
		// 	hostSocket.emit('SdpInfo', jsonObj, socket.id);
		// }
	});

	socket.on("IceInfo", function(jsonObj) {
		var roomId = jsonObj.roomId;
		console.log("IceInfo", jsonObj);
		// 这里也是这样，直播客给直播主发，直播主发给直播客
		if (livings_map.get(roomId) == socket.id) {
			// 如果是直播主发的,给所有人都发，其实给指定的直播客发就可以了,只是由于onIceCandidate的时候没法确认对方
			socket.to(roomId).emit("IceInfo", jsonObj, socket.id);
		} else {
			var hostSocekt = livings_map.get(roomId);
			hostSocekt.emit("IceInfo", jsonObj, socket.id);
		}
		socket.to(roomId).emit("IceInfo", jsonObj, socket.id);
		// socket.to(roomId).emit('IceInfo', jsonObj, socket.id);
	});

	socket.on('leave', (roomId)=> {
		// 这个，如果不按关闭，有可能让服务器的users统计错误
		// 但是如果没有连接，那么直接不管就好了
		var myRoom = sockio.sockets.adapter.rooms[roomId];
		if (null != myRoom) {			
			console.log("myRoom:", myRoom);
			console.log("sockets:", myRoom.sockets);
			var users = Object.keys(myRoom.sockets).length;
			users = users - 1;
			// 如果这个人是播主，那么主播室删掉这个房间
			livings_map.forEach(element => {
				if (element === roomId) {
					livings_map.delete(roomId);
				}
			});
			logger.log('the number of user in room is: ' + (users-1));
	
			socket.leave(roomId);
			socket.emit('leaved', roomId, socket.id);	
			socket.to(roomId).emit('bye', roomId, socket.id);	
		}
	 	//socket.to(room).emit('joined', room, socket.id);//除自己之外
		//io.in(room).emit('joined', room, socket.id)//房间内所有人
	 	//socket.broadcast.emit('joined', room, socket.id);//除自己，全部站点	
	});
	socket.on("online", (roomId) => {
		// 要在哪个房间直播，那么直接告诉这个房间的其他人，就如同otherjoin
		livings_map.set(roomId, socket)
		socket.to(roomId).emit('lineup', roomId, socket.id);//除自己之外
	});
});
// https_server.listen(4430, '0.0.0.0');