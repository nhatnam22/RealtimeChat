import React from 'react';

interface MessageProps {
    sender: string;
    content: string;
    isOwnMessage: boolean;
}

const Message: React.FC<MessageProps> = ({ sender, content, isOwnMessage }) => {
    return (
        <div className={`flex flex-col mb-3 ${isOwnMessage ? 'items-end' : 'items-start'}`}>
            <span className="text-xs text-gray-500 mb-1 ml-1 mr-1">
                {isOwnMessage ? 'Bạn' : sender}
            </span>
            <div
                className={`px-4 py-2 rounded-lg max-w-[70%] break-words ${
                    isOwnMessage
                        ? 'bg-blue-500 text-white rounded-br-none'
                        : 'bg-gray-200 text-gray-800 rounded-bl-none'
                }`}
            >
                {content}
            </div>
        </div>
    );
};

export default Message;
