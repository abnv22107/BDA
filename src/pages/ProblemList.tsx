import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Card,
  CardContent,
  Chip,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import axios from 'axios';

interface Problem {
  _id: string;
  title: string;
  difficulty: string;
  tags: string[];
  acceptanceRate: number;
  totalSubmissions: number;
}

const ProblemList: React.FC = () => {
  const navigate = useNavigate();
  const [problems, setProblems] = useState<Problem[]>([]);
  const [difficulty, setDifficulty] = useState<string>('');
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [selectedTags, setSelectedTags] = useState<string[]>([]);

  useEffect(() => {
    fetchProblems();
  }, [difficulty, selectedTags]);

  const fetchProblems = async () => {
    try {
      const params = new URLSearchParams();
      if (difficulty) params.append('difficulty', difficulty);
      if (selectedTags.length > 0) params.append('tags', selectedTags.join(','));

      const response = await axios.get(`/api/problems?${params.toString()}`);
      setProblems(response.data);
    } catch (error) {
      console.error('Error fetching problems:', error);
    }
  };

  const getDifficultyColor = (difficulty: string) => {
    switch (difficulty.toLowerCase()) {
      case 'easy':
        return '#00c853';
      case 'medium':
        return '#ff9100';
      case 'hard':
        return '#d50000';
      default:
        return '#757575';
    }
  };

  const filteredProblems = problems.filter((problem) =>
    problem.title.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Problems
      </Typography>

      <Grid container spacing={2} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={4}>
          <TextField
            fullWidth
            label="Search problems"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <FormControl fullWidth>
            <InputLabel>Difficulty</InputLabel>
            <Select
              value={difficulty}
              label="Difficulty"
              onChange={(e) => setDifficulty(e.target.value)}
            >
              <MenuItem value="">All</MenuItem>
              <MenuItem value="Easy">Easy</MenuItem>
              <MenuItem value="Medium">Medium</MenuItem>
              <MenuItem value="Hard">Hard</MenuItem>
            </Select>
          </FormControl>
        </Grid>
      </Grid>

      <TableContainer component={Card}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Title</TableCell>
              <TableCell>Difficulty</TableCell>
              <TableCell>Tags</TableCell>
              <TableCell align="right">Acceptance Rate</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredProblems.map((problem) => (
              <TableRow
                key={problem._id}
                hover
                onClick={() => navigate(`/problems/${problem._id}`)}
                sx={{ cursor: 'pointer' }}
              >
                <TableCell>{problem.title}</TableCell>
                <TableCell>
                  <Chip
                    label={problem.difficulty}
                    size="small"
                    sx={{
                      backgroundColor: getDifficultyColor(problem.difficulty),
                      color: 'white',
                    }}
                  />
                </TableCell>
                <TableCell>
                  {problem.tags.map((tag) => (
                    <Chip
                      key={tag}
                      label={tag}
                      size="small"
                      sx={{ mr: 0.5, mb: 0.5 }}
                    />
                  ))}
                </TableCell>
                <TableCell align="right">
                  {(problem.acceptanceRate * 100).toFixed(1)}%
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default ProblemList; 