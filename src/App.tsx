import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Layout from './components/Layout';
import Home from './pages/Home';
import ProblemList from './pages/ProblemList';
import ProblemDetail from './pages/ProblemDetail';
import ContestList from './pages/ContestList';
import ContestDetail from './pages/ContestDetail';
import Profile from './pages/Profile';
import Analytics from './pages/Analytics';

const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: '#90caf9',
    },
    secondary: {
      main: '#f48fb1',
    },
  },
});

function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Router>
        <Layout>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/problems" element={<ProblemList />} />
            <Route path="/problems/:id" element={<ProblemDetail />} />
            <Route path="/contests" element={<ContestList />} />
            <Route path="/contests/:id" element={<ContestDetail />} />
            <Route path="/profile" element={<Profile />} />
            <Route path="/analytics" element={<Analytics />} />
          </Routes>
        </Layout>
      </Router>
    </ThemeProvider>
  );
}

export default App; 