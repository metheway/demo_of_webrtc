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

// var http_server = http.createServer(app);
// http_server.listen(8080, '0.0.0.0');

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

//connection
// io.sockets.on('connection', (socket)=>{	
		// socket.to(room).emit('bye', room, socket.id)//房间内所有人,除自己外
	 	// socket.emit('leaved', room, socket.id);	
	 	//socket.to(room).emit('joined', room, socket.id);//除自己之外
		//io.in(room).emit('joined', room, socket.id)//房间内所有人
	 	//socket.broadcast.emit('joined', room, socket.id);//除自己，全部站点	
//connection

// 应该加锁
console.log("conn listening");
sockio.sockets.on('connection', (socket)=>{
	
	console.log("connected");
	// 连接以后就监听交换信令／candidates的信息
	socket.on('message', (room, data)=>{
		sockio.in(room).emit('message', room, socket.id, data)//房间内所有人
	});

	socket.on('join', (room) => {
		socket.join(room);
		var myRoom = sockio.sockets.adapter.rooms[room];
		console.log("myRoom:", myRoom);
		var users = Object.keys(myRoom.sockets).length;
		console.log("join receive, users:", users);
		logger.log('the number of user in room is: ' + users);
		
		// 加入的人不能过多，一对一通信
		if(users < 3) {
			socket.emit('joined', room, socket.id);	
			users++; 
			// 没写好leave逻辑，暂时不用，防止调试问题
			if (users > 1) {
				socket.to(room).emit('otherjoin', room);//除自己之外
			}
			// 其他人开始交换SDP
		}else {
			// 见这个用户移除并告知已经满人了,
			socket.leave(room);
			socket.emit('full', room, socket.id);	
		}

	 	//socket.to(room).emit('joined', room, socket.id);//除自己之外
		//io.in(room).emit('joined', room, socket.id)//房间内所有人
		//socket.broadcast.emit('joined', room, socket.id);//除自己，全部站点
		 
		// for test
		// socket.emit('otherjoin', room, socket.id);
	});

	socket.on("SdpInfo", function(jsonObj) {
		console.log("for test:" + jsonObj.roomId);
		var roomId = jsonObj.roomId;
		socket.to(roomId).emit('SdpInfo', jsonObj, socket.id);
	});

	socket.on("IceInfo", function(jsonObj) {
		var roomId = jsonObj.roomId;
		console.log("IceInfo", jsonObj);
		socket.to(roomId).emit('IceInfo', jsonObj, socket.id);
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
	
			logger.log('the number of user in room is: ' + (users-1));
	
			socket.leave(roomId);
			socket.emit('leaved', roomId, socket.id);	
			socket.to(roomId).emit('bye', roomId, socket.id);	
		}
	 	//socket.to(room).emit('joined', room, socket.id);//除自己之外
		//io.in(room).emit('joined', room, socket.id)//房间内所有人
	 	//socket.broadcast.emit('joined', room, socket.id);//除自己，全部站点	
	});
});
// https_server.listen(4430, '0.0.0.0');