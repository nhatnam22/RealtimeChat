import React, { useState } from 'react';

interface AuthProps {
  onLoginSuccess: (username: string, token: string) => void;
}

const API_BASE_URL = 'http://localhost:8080/api';

const Auth: React.FC<AuthProps> = ({ onLoginSuccess }) => {
  const [isLoginView, setIsLoginView] = useState(true);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState(''); // Only used for register
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      if (isLoginView) {
        const response = await fetch(`${API_BASE_URL}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password }),
        });

        if (response.ok) {
          const token = await response.text();
          localStorage.setItem('token', token);
          onLoginSuccess(username, token);
        } else {
          const errorMsg = await response.text();
          setError(errorMsg || 'Invalid username or password');
        }
      } else {
        const response = await fetch(`${API_BASE_URL}/auth/register`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password, email }),
        });

        if (response.ok) {
          // Auto switch to login view or directly login
          setIsLoginView(true);
          setError(null);
          alert('User registered successfully. Please login.');
          setPassword('');
        } else {
          const errorMsg = await response.text();
          setError(errorMsg || 'Registration failed');
        }
      }
    } catch (err) {
      setError('Cannot connect to server.');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center h-screen bg-gray-100">
      <div className="bg-white p-8 rounded shadow-md w-96">
        <h1 className="text-2xl font-bold mb-6 text-center">
          {isLoginView ? 'Đăng nhập Chat' : 'Đăng ký Tài khoản'}
        </h1>
        
        {error && (
          <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <input
            type="text"
            className="w-full p-3 border rounded focus:outline-none focus:border-blue-500"
            placeholder="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
          
          {!isLoginView && (
            <input
              type="email"
              className="w-full p-3 border rounded focus:outline-none focus:border-blue-500"
              placeholder="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required={!isLoginView}
            />
          )}

          <input
            type="password"
            className="w-full p-3 border rounded focus:outline-none focus:border-blue-500"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />

          <button
            type="submit"
            disabled={loading || username.trim() === '' || password.trim() === ''}
            className="bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded mt-2 disabled:bg-blue-300 transition-colors"
          >
            {loading ? 'Đang xử lý...' : (isLoginView ? 'Đăng nhập' : 'Đăng ký')}
          </button>
        </form>

        <div className="mt-4 text-center">
          <button
            onClick={() => {
              setIsLoginView(!isLoginView);
              setError(null);
            }}
            className="text-blue-500 hover:underline text-sm"
          >
            {isLoginView ? 'Chưa có tài khoản? Đăng ký ngay' : 'Đã có tài khoản? Đăng nhập'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default Auth;
