import React, { useEffect, useState, useRef } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import Message from './Message';

interface ChatProps {
    username: string;
    token: string;
    onLogout: () => void;
}

interface ChatMessage {
    sender: string;
    recipient?: string;
    groupId?: string;
    content: string;
    type: 'CHAT' | 'TYPING' | 'JOIN' | 'LEAVE';
}

const API_BASE_URL = 'http://localhost:8080';

const Chat: React.FC<ChatProps> = ({ username, token, onLogout }) => {
    const [onlineUsers, setOnlineUsers] = useState<string[]>([]);
    const [messagesMap, setMessagesMap] = useState<Record<string, ChatMessage[]>>({});
    const [activeChat, setActiveChat] = useState<string | null>(null);
    const [messageInput, setMessageInput] = useState<string>('');
    const [typingStatus, setTypingStatus] = useState<Record<string, boolean>>({});
    
    const clientRef = useRef<Client | null>(null);
    const typingTimeoutRef = useRef<Record<string, NodeJS.Timeout>>({});
    const emitTypingTimeoutRef = useRef<NodeJS.Timeout | null>(null);
    const messagesEndRef = useRef<HTMLDivElement>(null);

    // Scroll to bottom when messages change
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messagesMap, activeChat, typingStatus]);

    useEffect(() => {
        // 1. Fetch online users
        const fetchOnlineUsers = async () => {
            try {
                const res = await fetch(`${API_BASE_URL}/api/users/online`, {
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                if (res.ok) {
                    const data: string[] = await res.json();
                    // Filter out our own username
                    setOnlineUsers(data.filter(u => u !== username));
                }
            } catch (err) {
                console.error("Failed to fetch online users", err);
            }
        };

        fetchOnlineUsers();

        // 2. Initialize STOMP connection
        const client = new Client({
            webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
            connectHeaders: {
                token: token
            },
            reconnectDelay: 5000,
            debug: (str) => {
                console.log(str);
            },
            onConnect: () => {
                console.log('Connected to WebSocket');
                
                // Subscribe to Public channel for JOIN/LEAVE
                client.subscribe('/topic/public', (message: IMessage) => {
                    const msg: ChatMessage = JSON.parse(message.body);
                    if (msg.sender === username) return;

                    if (msg.type === 'JOIN') {
                        setOnlineUsers(prev => {
                            if (!prev.includes(msg.sender)) return [...prev, msg.sender];
                            return prev;
                        });
                    } else if (msg.type === 'LEAVE') {
                        setOnlineUsers(prev => prev.filter(u => u !== msg.sender));
                    }
                });

                // Subscribe to Private messages channel
                client.subscribe('/user/queue/messages', (message: IMessage) => {
                    const msg: ChatMessage = JSON.parse(message.body);
                    
                    if (msg.type === 'CHAT') {
                        setMessagesMap(prev => {
                            const chatWith = msg.sender; // Message is from sender
                            const existing = prev[chatWith] || [];
                            return { ...prev, [chatWith]: [...existing, msg] };
                        });
                    } else if (msg.type === 'TYPING') {
                        // Handle receiving typing indicator
                        const sender = msg.sender;
                        setTypingStatus(prev => ({ ...prev, [sender]: true }));
                        
                        // Clear existing timeout for this sender
                        if (typingTimeoutRef.current[sender]) {
                            clearTimeout(typingTimeoutRef.current[sender]);
                        }
                        // Set new timeout to clear typing status
                        typingTimeoutRef.current[sender] = setTimeout(() => {
                            setTypingStatus(prev => ({ ...prev, [sender]: false }));
                        }, 3000);
                    }
                });
            },
            onStompError: (frame) => {
                console.error('Broker reported error: ' + frame.headers['message']);
                console.error('Additional details: ' + frame.body);
            }
        });

        client.activate();
        clientRef.current = client;

        return () => {
            if (clientRef.current) {
                clientRef.current.deactivate();
            }
            // Clear any pending timeouts
            Object.values(typingTimeoutRef.current).forEach(clearTimeout);
            if (emitTypingTimeoutRef.current) clearTimeout(emitTypingTimeoutRef.current);
        };
    }, [token, username]);

    const handleSendMessage = () => {
        if (!activeChat || !messageInput.trim() || !clientRef.current) return;

        const chatMessage: ChatMessage = {
            sender: username,
            recipient: activeChat,
            content: messageInput.trim(),
            type: 'CHAT'
        };

        // Publish to server
        clientRef.current.publish({
            destination: '/app/chat.sendMessage',
            body: JSON.stringify(chatMessage)
        });

        // Add to local state immediately
        setMessagesMap(prev => {
            const existing = prev[activeChat] || [];
            return { ...prev, [activeChat]: [...existing, chatMessage] };
        });

        setMessageInput('');
    };

    const handleTyping = (e: React.ChangeEvent<HTMLInputElement>) => {
        setMessageInput(e.target.value);

        if (!activeChat || !clientRef.current) return;

        // Throttle sending typing indicator to server (every 2 seconds)
        if (!emitTypingTimeoutRef.current) {
            const typingMsg: ChatMessage = {
                sender: username,
                recipient: activeChat,
                content: '',
                type: 'TYPING'
            };
            
            clientRef.current.publish({
                destination: '/app/chat.typing',
                body: JSON.stringify(typingMsg)
            });

            emitTypingTimeoutRef.current = setTimeout(() => {
                emitTypingTimeoutRef.current = null;
            }, 2000);
        }
    };

    return (
        <div className="flex h-screen bg-gray-100 font-sans">
            {/* Sidebar: Online Users */}
            <div className="w-1/4 bg-white border-r flex flex-col">
                <div className="p-4 bg-blue-600 text-white flex justify-between items-center">
                    <span className="font-bold">Chat App</span>
                    <button 
                        onClick={onLogout} 
                        className="text-sm bg-red-500 hover:bg-red-600 px-3 py-1 rounded transition-colors"
                    >
                        Đăng xuất
                    </button>
                </div>
                <div className="p-4 border-b bg-gray-50">
                    <p className="text-sm text-gray-600">Xin chào, <span className="font-bold text-gray-800">{username}</span></p>
                </div>
                <div className="flex-1 overflow-y-auto p-4">
                    <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-3">Người dùng Online</h2>
                    {onlineUsers.length === 0 ? (
                        <p className="text-sm text-gray-400 italic">Không có ai online</p>
                    ) : (
                        <ul className="space-y-2">
                            {onlineUsers.map(user => (
                                <li 
                                    key={user} 
                                    className={`p-3 rounded-lg cursor-pointer flex items-center gap-3 transition-colors ${activeChat === user ? 'bg-blue-100 border-blue-500 border' : 'hover:bg-gray-100 border border-transparent'}`}
                                    onClick={() => setActiveChat(user)}
                                >
                                    <div className="w-2 h-2 bg-green-500 rounded-full"></div>
                                    <span className="font-medium text-gray-700">{user}</span>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            </div>

            {/* Main Chat Area */}
            <div className="w-3/4 flex flex-col bg-white">
                {activeChat ? (
                    <>
                        <div className="p-4 border-b flex items-center gap-3 bg-white shadow-sm z-10">
                            <h2 className="text-lg font-bold text-gray-800">Trò chuyện với {activeChat}</h2>
                            <div className="w-2 h-2 bg-green-500 rounded-full"></div>
                        </div>

                        <div className="flex-1 p-6 overflow-y-auto bg-gray-50">
                            {(messagesMap[activeChat] || []).map((msg, idx) => (
                                <Message
                                    key={idx}
                                    sender={msg.sender}
                                    content={msg.content}
                                    isOwnMessage={msg.sender === username}
                                />
                            ))}
                            {typingStatus[activeChat] && (
                                <div className="text-sm text-gray-500 italic ml-2 animate-pulse">
                                    {activeChat} đang gõ...
                                </div>
                            )}
                            <div ref={messagesEndRef} />
                        </div>

                        <div className="p-4 bg-white border-t">
                            <div className="flex gap-2">
                                <input
                                    type="text"
                                    className="flex-1 p-3 border rounded-lg focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition-all"
                                    placeholder="Nhập tin nhắn..."
                                    value={messageInput}
                                    onChange={handleTyping}
                                    onKeyDown={(e) => {
                                        if (e.key === 'Enter') handleSendMessage();
                                    }}
                                />
                                <button
                                    onClick={handleSendMessage}
                                    disabled={!messageInput.trim()}
                                    className="bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white font-bold py-2 px-6 rounded-lg transition-colors"
                                >
                                    Gửi
                                </button>
                            </div>
                        </div>
                    </>
                ) : (
                    <div className="flex-1 flex items-center justify-center bg-gray-50 flex-col gap-4">
                        <div className="text-gray-400">
                            <svg className="w-24 h-24 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" /></svg>
                        </div>
                        <p className="text-gray-500 text-lg">Chọn một người dùng bên trái để bắt đầu trò chuyện</p>
                    </div>
                )}
            </div>
        </div>
    );
};

export default Chat;
