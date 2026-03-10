import java.util.*;

class TracksState {

    // ── Algorithm enum ────────────────────────────────────────────────────────
    enum CpuAlgorithm { GREEDY, DIVIDE_AND_CONQUER, DP_BACKTRACKING }

    // ── GREEDY weights ────────────────────────────────────────────────────────
    private static final double G_DIST      = 50.0;
    private static final double G_DEGREE_OK = 20.0;
    private static final double G_CONN      = 60.0;
    private static final double G_DISCONN   = -200.0;
    private static final double G_SLACK     = 20.0;
    private static final double PEN_DEGREE  = -200.0;
    private static final double PEN_UNREACH = -500.0;

    // ── D&C seam ──────────────────────────────────────────────────────────────
    private final int MID_COL;

    // ── TRUE DP+BT memo table ─────────────────────────────────────────────────
    private final Map<StateKey, Integer> memo = new HashMap<>();

    // ── Core fields ───────────────────────────────────────────────────────────
    final TracksPuzzle puzzle;
    final GridGraph    graph;
    public final Stack<GameMove> moveHistory = new Stack<>();

    private final int[] rowCounts, colCounts;
    private Map<GraphNode, Integer> distFromStart, distToEnd;

    private CpuAlgorithm cpuAlgorithm = CpuAlgorithm.GREEDY;
    private Pos lastCpuPlaced = null;

    // ── Constructor ───────────────────────────────────────────────────────────
    TracksState(TracksPuzzle puzzle) {
        this.puzzle    = puzzle;
        this.graph     = new GridGraph(puzzle.rows, puzzle.cols);
        this.rowCounts = new int[puzzle.rows];
        this.colCounts = new int[puzzle.cols];
        this.MID_COL   = puzzle.cols / 2;

        for (GraphNode n : graph.getAllNodes()) n.state = CellState.EMPTY;
        placeTrack(puzzle.start.r, puzzle.start.c);
        placeTrack(puzzle.end.r,   puzzle.end.c);
        for (Pos p : puzzle.prefilledTracks)
            if (!p.equals(puzzle.start) && !p.equals(puzzle.end))
                placeTrack(p.r, p.c);
    }

    // ── Algorithm API ─────────────────────────────────────────────────────────
    public CpuAlgorithm getCpuAlgorithm()               { return cpuAlgorithm; }
    public void         setCpuAlgorithm(CpuAlgorithm a) { cpuAlgorithm = a; }
    public Pos          getLastCpuPlaced()               { return lastCpuPlaced; }
    public boolean      isCpuLockedTrack(int r, int c)  { return false; }

    public String getCpuAlgorithmLabel() {
        switch (cpuAlgorithm) {
            case DIVIDE_AND_CONQUER: return "Divide & Conquer";
            case DP_BACKTRACKING:   return "DP + Backtracking";
            default:                return "Greedy";
        }
    }

    public String explainInvalidTrackPlacement(int r, int c) {
        if (rowCounts[r] >= puzzle.rowCounts[r])
            return "Row " + r + " quota (" + puzzle.rowCounts[r] + ") is already full.";
        if (colCounts[c] >= puzzle.colCounts[c])
            return "Col " + c + " quota (" + puzzle.colCounts[c] + ") is already full.";
        return null;
    }

    int       getRows()             { return puzzle.rows; }
    int       getCols()             { return puzzle.cols; }
    CellState getCell(int r, int c) { return graph.getNode(r, c).state; }

    // ── State mutation ────────────────────────────────────────────────────────
    private void placeTrack(int r, int c) {
        GraphNode n = graph.getNode(r, c);
        if (n.state != CellState.TRACK) {
            n.state = CellState.TRACK;
            rowCounts[r]++; colCounts[c]++;
        }
    }

    void setCellWithHistory(int r, int c, CellState s) {
        GraphNode n    = graph.getNode(r, c);
        CellState prev = n.state;
        if (prev == s) return;
        if (prev == CellState.TRACK) { rowCounts[r]--; colCounts[c]--; }
        if (s    == CellState.TRACK) { rowCounts[r]++; colCounts[c]++; }
        moveHistory.push(new GameMove(r, c, prev, s));
        n.state = s;
        invalidateBFS();
    }

    boolean canUndo() { return !moveHistory.isEmpty(); }

    void undo() {
        if (moveHistory.isEmpty()) return;
        GameMove  m = moveHistory.pop();
        GraphNode n = graph.getNode(m.r, m.c);
        if (n.state     == CellState.TRACK) { rowCounts[m.r]--; colCounts[m.c]--; }
        n.state = m.prevState;
        if (m.prevState == CellState.TRACK) { rowCounts[m.r]++; colCounts[m.c]++; }
        invalidateBFS();
    }

    private void invalidateBFS() { distFromStart = distToEnd = null; }

    // ── Player move ───────────────────────────────────────────────────────────
    boolean isValidPlayerMove(int r, int c, CellState s) {
        if (isEndpoint(r, c)) return false;
        GraphNode n = graph.getNode(r, c);
        if (n.state == s) return true;
        if (s == CellState.TRACK) {
            if (rowCounts[r] >= puzzle.rowCounts[r]) return false;
            if (colCounts[c] >= puzzle.colCounts[c]) return false;
            for (GraphNode nb : n.neighbors)
                if (nb.state == CellState.TRACK) return true;
            return false;
        }
        return true;
    }

    public boolean isHintCell(int r, int c) {
        for (Pos p : puzzle.prefilledTracks)
            if (p.r == r && p.c == c) return true;
        return false;
    }

    void cycleCell(int r, int c) {
        if (isEndpoint(r, c)) return;
        if (isHintCell(r, c)) return;   // hint cells are fixed — player cannot remove them
        GraphNode n = graph.getNode(r, c);
        if (n.state == CellState.BLOCKED) return;
        CellState next = (n.state == CellState.EMPTY) ? CellState.TRACK : CellState.EMPTY;
        if (isValidPlayerMove(r, c, next)) setCellWithHistory(r, c, next);
    }

    private boolean isEndpoint(int r, int c) {
        return (r == puzzle.start.r && c == puzzle.start.c)
            || (r == puzzle.end.r   && c == puzzle.end.c);
    }
    private boolean isEndpointNode(GraphNode n) {
        return isEndpoint(n.position.r, n.position.c);
    }

    // ── CPU dispatcher ────────────────────────────────────────────────────────
    public boolean cpuMove() {
        lastCpuPlaced = null;
        switch (cpuAlgorithm) {
            case DIVIDE_AND_CONQUER: return cpuMoveDnC();
            case DP_BACKTRACKING:   return cpuMoveDPBT();
            default:                return cpuMoveGreedy();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ALGORITHM 1 — GREEDY
    //
    //  QuickSort frontier by distToEnd, then score each candidate:
    //    score = G_DIST/(1+dist)         ← closeness to B
    //          + G_DEGREE_OK             ← valid degree reward
    //          + G_CONN / G_DISCONN      ← connected to start or not
    //          + rowSlack * G_SLACK      ← row has capacity remaining
    //          + colSlack * G_SLACK      ← col has capacity remaining
    //
    //  Picks highest-scoring candidate.
    //  Works on ANY row/col constraint because rowSlack/colSlack
    //  normalise against the puzzle's own targets, and exceedsLimits()
    //  hard-rejects any placement that would overflow a quota.
    // ══════════════════════════════════════════════════════════════════════════
    private boolean cpuMoveGreedy() {
        System.out.println("\n── GREEDY ─────────────────────────────────────────");
        if (isSolved()) return false;
        List<GraphNode> frontier = getPathTipFrontier();
        if (frontier.isEmpty()) { autoBlock(); return false; }

        ensureBFS();
        quickSort(frontier, 0, frontier.size() - 1);

        GraphNode best      = null;
        double    bestScore = Double.NEGATIVE_INFINITY;
        for (GraphNode c : frontier) {
            double s = greedyScore(c);
            System.out.printf("  %-10s dist=%-3s conn=%-5s rowSlack=%.2f colSlack=%.2f → %.2f%n",
                c.position,
                distToEnd.containsKey(c) ? distToEnd.get(c) : "∞",
                isTrackReachableFromStart(c) ? "yes" : "no",
                rowSlack(c.position.r), colSlack(c.position.c), s);
            if (s > bestScore) { bestScore = s; best = c; }
        }
        if (best == null) { autoBlock(); return false; }
        System.out.printf("→ GREEDY picks %s (score=%.2f)%n", best.position, bestScore);
        setCellWithHistory(best.position.r, best.position.c, CellState.TRACK);
        lastCpuPlaced = best.position;
        autoBlock();
        return true;
    }

    private double greedyScore(GraphNode c) {
        c.state = CellState.TRACK;
        rowCounts[c.position.r]++; colCounts[c.position.c]++;

        if (exceedsLimits() || c.getDegree() > 2) {
            c.state = CellState.EMPTY;
            rowCounts[c.position.r]--; colCounts[c.position.c]--;
            return PEN_DEGREE;
        }

        Integer d = distToEnd.get(c);
        if (d == null) {
            c.state = CellState.EMPTY;
            rowCounts[c.position.r]--; colCounts[c.position.c]--;
            return PEN_UNREACH;
        }

        boolean conn = isTrackReachableFromStart(c);
        double score = G_DIST / (1.0 + d)
                     + G_DEGREE_OK
                     + (conn ? G_CONN : G_DISCONN)
                     + rowSlack(c.position.r) * G_SLACK
                     + colSlack(c.position.c) * G_SLACK;

        c.state = CellState.EMPTY;
        rowCounts[c.position.r]--; colCounts[c.position.c]--;
        return score;
    }

    private boolean isTrackReachableFromStart(GraphNode target) {
        GraphNode s = graph.getNode(puzzle.start);
        Set<GraphNode> vis = new HashSet<>();
        Deque<GraphNode> q = new ArrayDeque<>();
        q.add(s); vis.add(s);
        while (!q.isEmpty()) {
            GraphNode cur = q.removeFirst();
            for (GraphNode nb : cur.getTrackNeighbors())
                if (vis.add(nb)) q.addLast(nb);
        }
        for (GraphNode nb : target.neighbors)
            if (nb.state == CellState.TRACK && vis.contains(nb)) return true;
        return false;
    }

    private double rowSlack(int r) {
        int t = puzzle.rowCounts[r];
        return t == 0 ? 0.0 : Math.max(0.0, (double)(t - rowCounts[r]) / t);
    }
    private double colSlack(int c) {
        int t = puzzle.colCounts[c];
        return t == 0 ? 0.0 : Math.max(0.0, (double)(t - colCounts[c]) / t);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ALGORITHM 2 — DIVIDE & CONQUER
    //
    //  DIVIDE: split frontier by MID_COL seam into LEFT and RIGHT halves.
    //
    //  CONQUER: score each half independently.
    //    Left  rewards closeness to B + closeness to seam (rightward progress).
    //    Right rewards closeness to B + closeness to seam (leftward progress).
    //    Degree and quota constraints enforced before scoring.
    //
    //  COMBINE: extend the half that has MORE remaining column quota,
    //    so both halves grow toward the seam evenly. If a tip has already
    //    crossed the seam, extend the OTHER half to catch up.
    //
    //  Works on ANY row/col constraint because quota checks use
    //  puzzle.rowCounts/colCounts directly and remainingQuotaInCols
    //  computes live remaining capacity.
    // ══════════════════════════════════════════════════════════════════════════
    private boolean cpuMoveDnC() {
        System.out.println("\n── DIVIDE & CONQUER  (seam col=" + MID_COL + ") ───────────");
        if (isSolved()) return false;

        ensureBFS();
        List<GraphNode> frontier = getPathTipFrontier();
        if (frontier.isEmpty()) { autoBlock(); return false; }

        // ── DIVIDE ────────────────────────────────────────────────────────────
        List<GraphNode> leftFrontier  = new ArrayList<>();
        List<GraphNode> rightFrontier = new ArrayList<>();
        for (GraphNode n : frontier) {
            if (n.position.c < MID_COL) leftFrontier.add(n);
            else                         rightFrontier.add(n);
        }
        System.out.println("  LEFT  candidates: " + leftFrontier.size()
            + "  (cols 0.." + (MID_COL-1) + ")");
        System.out.println("  RIGHT candidates: " + rightFrontier.size()
            + "  (cols " + MID_COL + ".." + (puzzle.cols-1) + ")");

        // ── CONQUER ───────────────────────────────────────────────────────────
        GraphNode leftBest  = conquerHalf(leftFrontier,  true);
        GraphNode rightBest = conquerHalf(rightFrontier, false);

        // ── Find both chain tips ───────────────────────────────────────────────
        GraphNode tipFromStart = getChainTipFromStart();
        GraphNode tipFromEnd   = getChainTipFromEnd();
        System.out.println("  Tip from START: "
            + (tipFromStart != null ? tipFromStart.position : "none"));
        System.out.println("  Tip from END  : "
            + (tipFromEnd   != null ? tipFromEnd.position   : "none"));

        // ── COMBINE ───────────────────────────────────────────────────────────
        int leftRemaining  = remainingQuotaInCols(0, MID_COL - 1);
        int rightRemaining = remainingQuotaInCols(MID_COL, puzzle.cols - 1);
        System.out.println("  Remaining quota: LEFT=" + leftRemaining
            + "  RIGHT=" + rightRemaining);

        GraphNode chosen = combine(leftBest, rightBest,
                                   leftRemaining, rightRemaining,
                                   tipFromStart, tipFromEnd);

        if (chosen == null) {
            System.out.println("→ D&C: no valid candidate — greedy fallback");
            return cpuMoveGreedy();
        }

        System.out.println("→ D&C picks " + chosen.position
            + "  (" + (chosen.position.c < MID_COL ? "LEFT" : "RIGHT") + " half)");
        setCellWithHistory(chosen.position.r, chosen.position.c, CellState.TRACK);
        lastCpuPlaced = chosen.position;
        autoBlock();
        return true;
    }

    private GraphNode conquerHalf(List<GraphNode> half, boolean isLeft) {
        if (half.isEmpty()) return null;
        GraphNode best      = null;
        double    bestScore = Double.NEGATIVE_INFINITY;

        for (GraphNode c : half) {
            if (rowCounts[c.position.r] >= puzzle.rowCounts[c.position.r]) continue;
            if (colCounts[c.position.c] >= puzzle.colCounts[c.position.c]) continue;

            c.state = CellState.TRACK;
            rowCounts[c.position.r]++; colCounts[c.position.c]++;
            boolean ok = !exceedsLimits() && c.getDegree() <= 2;
            c.state = CellState.EMPTY;
            rowCounts[c.position.r]--; colCounts[c.position.c]--;
            if (!ok) continue;

            Integer dEnd = distToEnd.get(c);
            if (dEnd == null) continue;

            int seamDist = Math.abs(c.position.c - MID_COL);
            double score = 80.0 / (1.0 + dEnd)
                         + 50.0 / (1.0 + seamDist);

            System.out.printf("    [%s] %-8s distToEnd=%-3d seamDist=%d → %.2f%n",
                isLeft ? "LEFT " : "RIGHT", c.position, dEnd, seamDist, score);

            if (score > bestScore) { bestScore = score; best = c; }
        }
        return best;
    }

    private GraphNode combine(GraphNode leftBest, GraphNode rightBest,
                               int leftRemaining, int rightRemaining,
                               GraphNode tipFromStart, GraphNode tipFromEnd) {
        if (leftBest  == null) return rightBest;
        if (rightBest == null) return leftBest;

        boolean startTipInRight = tipFromStart != null && tipFromStart.position.c >= MID_COL;
        boolean endTipInLeft    = tipFromEnd   != null && tipFromEnd.position.c   <  MID_COL;

        if (startTipInRight) {
            System.out.println("  COMBINE: start tip crossed seam → extend LEFT");
            return leftBest;
        }
        if (endTipInLeft) {
            System.out.println("  COMBINE: end tip crossed seam → extend RIGHT");
            return rightBest;
        }
        if (leftRemaining >= rightRemaining) {
            System.out.println("  COMBINE: leftRemaining=" + leftRemaining + " → extend LEFT");
            return leftBest;
        } else {
            System.out.println("  COMBINE: rightRemaining=" + rightRemaining + " → extend RIGHT");
            return rightBest;
        }
    }

    private GraphNode getChainTipFromStart() {
        GraphNode start = graph.getNode(puzzle.start);
        if (start == null || start.state != CellState.TRACK) return null;
        GraphNode prev = null, cur = start;
        while (true) {
            GraphNode next = null;
            for (GraphNode nb : cur.getTrackNeighbors())
                if (!nb.equals(prev)) { next = nb; break; }
            if (next == null || next.equals(start)) break;
            prev = cur; cur = next;
        }
        return cur;
    }

    private GraphNode getChainTipFromEnd() {
        GraphNode end = graph.getNode(puzzle.end);
        if (end == null || end.state != CellState.TRACK) return null;
        GraphNode prev = null, cur = end;
        while (true) {
            GraphNode next = null;
            for (GraphNode nb : cur.getTrackNeighbors())
                if (!nb.equals(prev)) { next = nb; break; }
            if (next == null || next.equals(end)) break;
            prev = cur; cur = next;
        }
        return cur;
    }

    private int remainingQuotaInCols(int lo, int hi) {
        int remaining = 0;
        for (int c = lo; c <= hi; c++)
            remaining += Math.max(0, puzzle.colCounts[c] - colCounts[c]);
        return remaining;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ALGORITHM 3 — TRUE DP + BACKTRACKING
    //
    //  BACKTRACKING — exhaustive recursive search:
    //    Every legal frontier cell is tried, placed, recursed into, then
    //    undone. No artificial branch limit. Guaranteed optimal solution.
    //
    //  TRUE DP MEMOIZATION:
    //    StateKey = BitSet(which cells are TRACK) + rowCounts[] + colCounts[].
    //    memo[StateKey] = proven minimum extra placements to reach solved state.
    //    Overlapping subproblems resolved instantly via memo lookup.
    //
    //  BRANCH-AND-BOUND:
    //    If depth + memo[state] >= bestCost → prune. DP lower bound drives BT.
    //
    //  CPU commits only the FIRST cell of the optimal solution path found.
    // ══════════════════════════════════════════════════════════════════════════
    private boolean cpuMoveDPBT() {
        System.out.println("\n── TRUE DP + BACKTRACKING ──────────────────");
        if (isSolved()) return false;

        List<GraphNode> frontier = getFrontierForDPBT();
        if (frontier.isEmpty()) { autoBlock(); return false; }

        memo.clear();
        List<GraphNode> solutionPath = new ArrayList<>();
        int[] bestCost = { Integer.MAX_VALUE };

        System.out.println("Starting BT+DP exhaustive search…");
        btSolve(new ArrayList<>(), solutionPath, bestCost, 0);

        if (solutionPath.isEmpty()) {
            System.out.println("  BT+DP: no solution found — fallback to first legal cell");
            for (GraphNode c : frontier) {
                if (tryPlaceTemp(c)) {
                    undoTempPlace(c);
                    setCellWithHistory(c.position.r, c.position.c, CellState.TRACK);
                    lastCpuPlaced = c.position;
                    autoBlock();
                    return true;
                }
            }
            autoBlock();
            return false;
        }

        GraphNode first = solutionPath.get(0);
        System.out.printf("→ DP+BT optimal path length=%d, commits %s%n",
            solutionPath.size(), first.position);
        System.out.print("  Full path: ");
        for (GraphNode n : solutionPath) System.out.print(n.position + " ");
        System.out.println();

        setCellWithHistory(first.position.r, first.position.c, CellState.TRACK);
        lastCpuPlaced = first.position;
        autoBlock();
        return true;
    }

    private void btSolve(List<GraphNode> currentPath,
                         List<GraphNode> bestPath,
                         int[]           bestCost,
                         int             depth) {
        if (isSolved()) {
            if (depth < bestCost[0]) {
                bestCost[0] = depth;
                bestPath.clear();
                bestPath.addAll(currentPath);
                System.out.println("  BT found solution, cost=" + depth);
                memo.put(captureState(), 0);
            }
            return;
        }

        StateKey key = captureState();
        if (memo.containsKey(key)) {
            int knownCost = memo.get(key);
            if (depth + knownCost >= bestCost[0]) {
                System.out.println("  DP prune at depth=" + depth + " (memo=" + knownCost + ")");
                return;
            }
        }

        if (depth >= bestCost[0]) return;

        List<GraphNode> frontier = getFrontierForDPBT();
        if (frontier.isEmpty()) {
            memo.put(key, Integer.MAX_VALUE / 2);
            return;
        }

        ensureBFS();
        frontier.sort((a, b) -> {
            int da = distToEnd.containsKey(a) ? distToEnd.get(a) : 999;
            int db = distToEnd.containsKey(b) ? distToEnd.get(b) : 999;
            if (da != db) return Integer.compare(da, db);
            return compareByPos(a, b);
        });

        int bestChildCost = Integer.MAX_VALUE / 2;
        for (GraphNode c : frontier) {
            if (!tryPlaceTemp(c)) continue;
            currentPath.add(c);
            int prevBest = bestCost[0];
            btSolve(currentPath, bestPath, bestCost, depth + 1);
            if (bestCost[0] < prevBest) {
                int childCost = bestCost[0] - depth;
                if (childCost < bestChildCost) bestChildCost = childCost;
            }
            currentPath.remove(currentPath.size() - 1);
            undoTempPlace(c);
        }

        if (!memo.containsKey(key) || memo.get(key) > bestChildCost)
            memo.put(key, bestChildCost == Integer.MAX_VALUE / 2
                         ? Integer.MAX_VALUE / 2 : bestChildCost);
    }

    private StateKey captureState() {
        int total = puzzle.rows * puzzle.cols;
        BitSet bits = new BitSet(total);
        for (int r = 0; r < puzzle.rows; r++)
            for (int c = 0; c < puzzle.cols; c++)
                if (graph.getNode(r, c).state == CellState.TRACK)
                    bits.set(r * puzzle.cols + c);
        return new StateKey(bits, rowCounts.clone(), colCounts.clone());
    }

    private boolean tryPlaceTemp(GraphNode c) {
        if (c.state != CellState.EMPTY) return false;
        if (rowCounts[c.position.r] >= puzzle.rowCounts[c.position.r]) return false;
        if (colCounts[c.position.c] >= puzzle.colCounts[c.position.c]) return false;
        c.state = CellState.TRACK;
        rowCounts[c.position.r]++;
        colCounts[c.position.c]++;
        if (c.getDegree() > 2) { undoTempPlace(c); return false; }
        for (GraphNode nb : c.neighbors) {
            if (nb.state != CellState.TRACK) continue;
            int deg = nb.getDegree();
            boolean ep = isEndpoint(nb.position.r, nb.position.c);
            if (ep && deg > 1)  { undoTempPlace(c); return false; }
            if (!ep && deg > 2) { undoTempPlace(c); return false; }
        }
        invalidateBFS();
        return true;
    }

    private void undoTempPlace(GraphNode c) {
        if (c.state == CellState.TRACK) {
            c.state = CellState.EMPTY;
            rowCounts[c.position.r]--;
            colCounts[c.position.c]--;
            invalidateBFS();
        }
    }

    private List<GraphNode> getFrontierForDPBT() {
        Set<GraphNode> seen = new HashSet<>();
        for (GraphNode t : graph.getTrackNodes())
            for (GraphNode nb : t.neighbors)
                if (nb.state == CellState.EMPTY
                    && rowCounts[nb.position.r] < puzzle.rowCounts[nb.position.r]
                    && colCounts[nb.position.c] < puzzle.colCounts[nb.position.c])
                    seen.add(nb);
        return new ArrayList<>(seen);
    }

    private int compareByPos(GraphNode a, GraphNode b) {
        if (a.position.r != b.position.r) return Integer.compare(a.position.r, b.position.r);
        return Integer.compare(a.position.c, b.position.c);
    }

    // ── QuickSort ─────────────────────────────────────────────────────────────
    private void quickSort(List<GraphNode> list, int lo, int hi) {
        if (lo >= hi) return;
        int pi = qsPartition(list, lo, hi);
        quickSort(list, lo, pi - 1);
        quickSort(list, pi + 1, hi);
    }
    private int qsPartition(List<GraphNode> list, int lo, int hi) {
        int pivot = distVal(list.get(hi));
        int i = lo - 1;
        for (int j = lo; j < hi; j++) {
            if (distVal(list.get(j)) <= pivot) {
                i++;
                GraphNode tmp = list.get(i); list.set(i, list.get(j)); list.set(j, tmp);
            }
        }
        GraphNode tmp = list.get(i+1); list.set(i+1, list.get(hi)); list.set(hi, tmp);
        return i + 1;
    }
    private int distVal(GraphNode n) {
        if (distToEnd == null) return 999;
        Integer d = distToEnd.get(n); return d != null ? d : 999;
    }

    // ── BFS ───────────────────────────────────────────────────────────────────
    private void ensureBFS() {
        if (distFromStart == null) distFromStart = bfs(graph.getNode(puzzle.start));
        if (distToEnd     == null) distToEnd     = bfs(graph.getNode(puzzle.end));
    }

    private Map<GraphNode, Integer> bfs(GraphNode src) {
        Map<GraphNode, Integer> d = new HashMap<>();
        if (src == null) return d;
        Deque<GraphNode> q = new ArrayDeque<>();
        q.add(src); d.put(src, 0);
        while (!q.isEmpty()) {
            GraphNode cur = q.removeFirst();
            int dd = d.get(cur);
            for (GraphNode nb : cur.neighbors)
                if (!d.containsKey(nb) && nb.state != CellState.BLOCKED) {
                    d.put(nb, dd + 1); q.addLast(nb);
                }
        }
        return d;
    }

    // ── Win condition ─────────────────────────────────────────────────────────
    boolean isSolved() {
        boolean cp = connectedPath();
        boolean ac = cp && allTrackConnected();
        System.out.println("isSolved: connected=" + cp + " allLinked=" + ac);
        return ac;
    }

    public boolean exactCounts() {
        for (int i = 0; i < puzzle.rows; i++)
            if (rowCounts[i] != puzzle.rowCounts[i]) return false;
        for (int i = 0; i < puzzle.cols; i++)
            if (colCounts[i] != puzzle.colCounts[i]) return false;
        return true;
    }

    public boolean connectedPath() {
        GraphNode s = graph.getNode(puzzle.start);
        GraphNode e = graph.getNode(puzzle.end);
        if (s.state != CellState.TRACK || e.state != CellState.TRACK) return false;
        Set<GraphNode> vis = new HashSet<>();
        Deque<GraphNode> q = new ArrayDeque<>();
        q.add(s); vis.add(s);
        while (!q.isEmpty()) {
            GraphNode cur = q.removeFirst();
            if (cur.equals(e)) return true;
            for (GraphNode nb : cur.getTrackNeighbors())
                if (vis.add(nb)) q.addLast(nb);
        }
        return false;
    }

    public boolean allTrackConnected() {
        GraphNode s = graph.getNode(puzzle.start);
        if (s.state != CellState.TRACK) return false;
        Set<GraphNode> all = graph.getTrackNodes();
        Set<GraphNode> vis = new HashSet<>();
        Deque<GraphNode> q = new ArrayDeque<>();
        q.add(s); vis.add(s);
        while (!q.isEmpty()) {
            GraphNode cur = q.removeFirst();
            for (GraphNode nb : cur.getTrackNeighbors())
                if (vis.add(nb)) q.addLast(nb);
        }
        return vis.size() == all.size();
    }

    // ── Frontier helpers ──────────────────────────────────────────────────────
    private List<GraphNode> getPathTipFrontier() {
        List<GraphNode> tips = findPathTips();
        Set<GraphNode>  seen = new HashSet<>();
        for (GraphNode tip : tips)
            for (GraphNode nb : tip.neighbors)
                if (nb.state == CellState.EMPTY
                        && rowCounts[nb.position.r] < puzzle.rowCounts[nb.position.r]
                        && colCounts[nb.position.c] < puzzle.colCounts[nb.position.c])
                    seen.add(nb);
        if (seen.isEmpty()) return getAnyFrontier();
        return new ArrayList<>(seen);
    }

    private List<GraphNode> findPathTips() {
        List<GraphNode> tips = new ArrayList<>();
        for (GraphNode t : graph.getTrackNodes()) {
            int td = t.getTrackNeighbors().size();
            if (isEndpointNode(t)) { if (td < 2)  tips.add(t); }
            else                   { if (td == 1) tips.add(t); }
        }
        if (tips.isEmpty()) {
            GraphNode s = graph.getNode(puzzle.start);
            GraphNode e = graph.getNode(puzzle.end);
            if (s != null) tips.add(s);
            if (e != null) tips.add(e);
        }
        return tips;
    }

    private List<GraphNode> getAnyFrontier() {
        Set<GraphNode> seen = new HashSet<>();
        for (GraphNode t : graph.getTrackNodes())
            for (GraphNode nb : t.neighbors)
                if (nb.state == CellState.EMPTY
                        && rowCounts[nb.position.r] < puzzle.rowCounts[nb.position.r]
                        && colCounts[nb.position.c] < puzzle.colCounts[nb.position.c])
                    seen.add(nb);
        return new ArrayList<>(seen);
    }

    private void autoBlock() {
        for (int r = 0; r < puzzle.rows; r++)
            if (rowCounts[r] == puzzle.rowCounts[r])
                for (int c = 0; c < puzzle.cols; c++)
                    if (graph.getNode(r, c).state == CellState.EMPTY)
                        setCellWithHistory(r, c, CellState.BLOCKED);
        for (int c = 0; c < puzzle.cols; c++)
            if (colCounts[c] == puzzle.colCounts[c])
                for (int r = 0; r < puzzle.rows; r++)
                    if (graph.getNode(r, c).state == CellState.EMPTY)
                        setCellWithHistory(r, c, CellState.BLOCKED);
    }

    private boolean exceedsLimits() {
        for (int i = 0; i < puzzle.rows; i++) if (rowCounts[i] > puzzle.rowCounts[i]) return true;
        for (int i = 0; i < puzzle.cols; i++) if (colCounts[i] > puzzle.colCounts[i]) return true;
        return false;
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    int[][] toIntGrid() {
        int[][] g = new int[puzzle.rows][puzzle.cols];
        for (int r = 0; r < puzzle.rows; r++)
            for (int c = 0; c < puzzle.cols; c++)
                g[r][c] = graph.getNode(r, c).state.toInt();
        return g;
    }

    int[]        getRowTrackCounts() { return rowCounts.clone(); }
    int[]        getColTrackCounts() { return colCounts.clone(); }

    Set<Integer> getBadRows() {
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < puzzle.rows; i++) if (rowCounts[i] > puzzle.rowCounts[i]) s.add(i);
        return s;
    }
    Set<Integer> getBadCols() {
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < puzzle.cols; i++) if (colCounts[i] > puzzle.colCounts[i]) s.add(i);
        return s;
    }

    // Returns the hint cell: a middle cell on the known solution path
    // that is currently EMPTY. Used by UI "Hint" feature.
    public Pos getHintCell() {
        // Collect all EMPTY cells that lie on a BFS path from start to end
        GraphNode s = graph.getNode(puzzle.start);
        GraphNode e = graph.getNode(puzzle.end);
        if (s == null || e == null) return null;

        // BFS from start through TRACK+EMPTY cells, record parent map
        Map<GraphNode, GraphNode> parent = new HashMap<>();
        Deque<GraphNode> q = new ArrayDeque<>();
        q.add(s); parent.put(s, null);
        while (!q.isEmpty()) {
            GraphNode cur = q.removeFirst();
            if (cur.equals(e)) break;
            for (GraphNode nb : cur.neighbors) {
                if (!parent.containsKey(nb) && nb.state != CellState.BLOCKED
                        && rowCounts[nb.position.r] < puzzle.rowCounts[nb.position.r]
                        && colCounts[nb.position.c] < puzzle.colCounts[nb.position.c]) {
                    parent.put(nb, cur);
                    q.addLast(nb);
                }
            }
        }
        if (!parent.containsKey(e)) return null;

        // Reconstruct path and find the middle EMPTY cell
        List<GraphNode> path = new ArrayList<>();
        GraphNode cur = e;
        while (cur != null) { path.add(cur); cur = parent.get(cur); }
        Collections.reverse(path);

        // Pick the middle empty cell of the path
        List<GraphNode> emptyCells = new ArrayList<>();
        for (GraphNode n : path)
            if (n.state == CellState.EMPTY) emptyCells.add(n);
        if (emptyCells.isEmpty()) return null;
        return emptyCells.get(emptyCells.size() / 2).position;
    }

    void resetToInitialState() {
        moveHistory.clear();
        memo.clear();
        for (GraphNode n : graph.getAllNodes()) n.state = CellState.EMPTY;
        Arrays.fill(rowCounts, 0); Arrays.fill(colCounts, 0);
        placeTrack(puzzle.start.r, puzzle.start.c);
        placeTrack(puzzle.end.r,   puzzle.end.c);
        for (Pos p : puzzle.prefilledTracks)
            if (!p.equals(puzzle.start) && !p.equals(puzzle.end))
                placeTrack(p.r, p.c);
        invalidateBFS();
        lastCpuPlaced = null;
    }

    // ── Inner types ───────────────────────────────────────────────────────────
    private static class StateKey {
        final BitSet trackBits;
        final int[]  rowCounts, colCounts;
        StateKey(BitSet bits, int[] rows, int[] cols) {
            this.trackBits = bits; this.rowCounts = rows; this.colCounts = cols;
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof StateKey)) return false;
            StateKey k = (StateKey) o;
            return trackBits.equals(k.trackBits)
                && Arrays.equals(rowCounts, k.rowCounts)
                && Arrays.equals(colCounts, k.colCounts);
        }
        @Override public int hashCode() {
            int h = trackBits.hashCode();
            h = 31 * h + Arrays.hashCode(rowCounts);
            h = 31 * h + Arrays.hashCode(colCounts);
            return h;
        }
    }
}