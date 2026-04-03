import React, { useState, useEffect } from 'react';
import './App.css';
import Chat from './components/Chat';
import Auth from './components/Auth';

const App: React.FC = () => {
  const [username, setUsername] = useState<string>('');
  const [token, setToken] = useState<string>('');
  const [isLoginedIn, setIsLoginedIn] = useState<boolean>(false);

  useEffect(() => {
    // Optional: Auto login if token exists (would need a validate API ideally)
    const savedToken = localStorage.getItem('token');
    const savedUsername = localStorage.getItem('username'); // You could save this during login too
    if (savedToken && savedUsername) {
      setToken(savedToken);
      setUsername(savedUsername);
      setIsLoginedIn(true);
    }
  }, []);

  const handleLoginSuccess = (loggedInUsername: string, loggedInToken: string) => {
    setUsername(loggedInUsername);
    setToken(loggedInToken);
    setIsLoginedIn(true);
    localStorage.setItem('username', loggedInUsername);
  };

  const handleLogout = () => {
    setIsLoginedIn(false);
    setUsername('');
    setToken('');
    localStorage.removeItem('token');
    localStorage.removeItem('username');
  };

  if (!isLoginedIn) {
    return <Auth onLoginSuccess={handleLoginSuccess} />;
  }

  return <Chat username={username} token={token} onLogout={handleLogout} />;
};

export default App;
