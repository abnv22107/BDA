// Connect to MongoDB
db = db.getSiblingDB('competitive_platform');

// Create collections
db.createCollection('users');
db.createCollection('problems');
db.createCollection('submissions');
db.createCollection('contests');

// Create indexes for users collection
db.users.createIndex({ "username": 1 }, { unique: true });
db.users.createIndex({ "email": 1 }, { unique: true });
db.users.createIndex({ "stats.totalSolved": -1 });

// Create indexes for problems collection
db.problems.createIndex({ "difficulty": 1 });
db.problems.createIndex({ "tags": 1 });
db.problems.createIndex({ "acceptanceRate": -1 });
db.problems.createIndex({ "createdAt": -1 });

// Create indexes for submissions collection
db.submissions.createIndex({ "userId": 1 });
db.submissions.createIndex({ "problemId": 1 });
db.submissions.createIndex({ "status": 1 });
db.submissions.createIndex({ "submittedAt": -1 });
db.submissions.createIndex({ "contestId": 1 }, { sparse: true });

// Create indexes for contests collection
db.contests.createIndex({ "startTime": 1 });
db.contests.createIndex({ "endTime": 1 });
db.contests.createIndex({ "visibility": 1 });
db.contests.createIndex({ "organizationId": 1 }, { sparse: true });

// Insert sample problems
db.problems.insertMany([
  {
    title: "Two Sum",
    description: "Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target.",
    difficulty: "Easy",
    tags: ["Array", "Hash Table"],
    constraints: "2 <= nums.length <= 10^4\n-10^9 <= nums[i] <= 10^9\n-10^9 <= target <= 10^9",
    inputFormat: "First line contains n (length of array)\nSecond line contains n space-separated integers\nThird line contains target sum",
    outputFormat: "Two space-separated integers representing indices",
    testCases: [
      {
        input: "4\n2 7 11 15\n9",
        expectedOutput: "0 1",
        isPublic: true,
        explanation: "nums[0] + nums[1] = 2 + 7 = 9"
      }
    ],
    acceptanceRate: 0.0,
    totalSubmissions: 0,
    successfulSubmissions: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
    timeLimit: 1000,
    memoryLimit: 128
  }
]);

// Create admin user
db.users.insertOne({
  username: "admin",
  email: "admin@example.com",
  passwordHash: "$2a$10$your_hashed_password", // Replace with actual hashed password
  fullName: "Admin User",
  role: "ADMIN",
  stats: {
    totalSolved: 0,
    easyProblemsSolved: 0,
    mediumProblemsSolved: 0,
    hardProblemsSolved: 0,
    averageAttempts: 0,
    averageTimePerProblem: 0,
    strongTags: [],
    weakTags: []
  },
  createdAt: new Date(),
  lastActive: new Date(),
  isVerified: true
}); 