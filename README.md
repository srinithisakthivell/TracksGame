# Tracks Puzzle Game – DAA Project

## Project Overview
The Tracks Puzzle Game is a Java-based puzzle application where the player must construct a valid track path between a **Start node** and an **End node** on a grid. The game demonstrates the use of graph-based algorithms and greedy strategies to solve path-building puzzles.

This project was developed as part of the **Design and Analysis of Algorithms (DAA)** course.

---

## Problem Statement
Given a grid-based puzzle with blocked cells and track segments, the objective is to connect the **start point** to the **end point** using valid track placements while respecting the puzzle constraints.

The system also provides:
- Hint generation
- Undo functionality
- CPU-assisted solving

---

## Algorithm Used
The project uses a **Greedy Strategy with Graph Representation**.

### Steps
1. Represent the grid as a **graph structure**.
2. Each cell acts as a **node**.
3. Adjacent valid cells are treated as **edges**.
4. The CPU solver selects the next optimal move based on greedy heuristics.

---

## Time Complexity

| Operation | Complexity |
|-----------|------------|
| Grid initialization | O(n²) |
| Move validation | O(1) |
| Hint generation | O(n²) |
| CPU solving | O(n²) |

Where **n represents grid size**.

---

## Technologies Used
- Java
- Java Swing (GUI)
- Graph Data Structures
- Greedy Algorithm

---

## Project Structure

TracksGame
│
├── src
│   ├── TracksSwingUI.java
│   ├── TracksPuzzle.java
│   ├── GridGraph.java
│   └── CellState.java
│
├── bin
│
├── .settings
│
├── .classpath
├── .project
└── README.md

---

## How to Run the Project

1. Navigate to the src folder

cd src

2. Compile the program

javac *.java

3. Run the application

java TracksSwingUI

---

## Features
- Interactive puzzle grid
- Hint system
- Undo move functionality
- CPU-assisted solving
- Graph-based puzzle logic

---

## Team Members
- Bharatheesha S
- Karthikkhashree E
- Manasha P Y
- S Srinithi

---

## Academic Use
This project was developed for **educational purposes as part of the DAA course** as a group.
