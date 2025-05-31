const express = require('express');
const http = require('http');
const socketIo = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

const users = {};

io.on('connection', (socket) => {
  console.log('New connection:', socket.id);

  // Register user
  socket.on('register', (username) => {
    users[username] = socket.id;
    socket.username = username;
    console.log(`${username} registered`);
    updateOnlineUsers();
  });

  // Private messaging
  socket.on('private message', (data) => {
    try {
      const { from, to, message, timestamp } = data;
      console.log(`Message from ${from} to ${to}`);

      const recipientSocketId = users[to];
      if (recipientSocketId) {
        io.to(recipientSocketId).emit('private message', {
          from,
          message,
          timestamp
        });
        // Send delivery confirmation
        io.to(users[from]).emit('message delivered', {
          to,
          messageId: Date.now()
        });
      } else {
        console.log('Recipient not found:', to);
      }
    } catch (e) {
      console.error('Message handling error:', e);
    }
  });

  // Update online users list
  function updateOnlineUsers() {
    io.emit('online users', Object.keys(users));
  }

  // Handle disconnection
  socket.on('disconnect', () => {
    if (socket.username) {
      delete users[socket.username];
      updateOnlineUsers();
      console.log(`${socket.username} disconnected`);
    }
  });
});

server.listen(3000, '0.0.0.0', () => {
  console.log('Server running on port 3000');
});