import java.awt.*;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.*;

public class TracksSwingUI extends JFrame {

    // ── State ────────────────────────────────────────────────────────────────
    private final TracksPuzzle puzzle;
    private final TracksState  state;
    private final JButton[][]  cells;
    private final JLabel[]     rowLabels, colLabels;

    // UI widgets
    private final JLabel    statusLabel;
    private final JLabel    algoLabel;
    private final JLabel    moveCountLabel;
    private final JPanel    logPanel;
    private final JButton   undoButton;
    private final JButton   hintButton;
    private final JComboBox<String> algoCombo;

    // Game flow
    private boolean gameOver     = false;
    private boolean playerTurn   = true;
    private int     cpuMoveCount = 0;
    private Pos     lastCpuPos   = null;
    private Pos     hintPos      = null;   // currently highlighted hint cell

    // Layout constants
    private static final int CELL = 56;
    private static final int GAP  =  2;
    private static final Color C_TRACK    = new Color(100, 149, 237);
    private static final Color C_BLOCKED  = new Color(210, 210, 210);
    private static final Color C_EMPTY    = Color.WHITE;
    private static final Color C_START    = new Color(255, 200,  50);
    private static final Color C_END      = new Color( 80, 200, 120);
    private static final Color C_LAST_CPU = new Color(255, 140,   0);
    private static final Color C_HINT     = new Color(255, 255, 0);  // purple — fixed hint cell
    private static final Color C_BAD      = new Color(220,  50,  50);
    private static final Color C_BG       = new Color(240, 242, 245);
    private static final Color C_PANEL    = new Color(255, 255, 255);

    // Matches CpuAlgorithm enum order: GREEDY, DIVIDE_AND_CONQUER, DP_BACKTRACKING
    private static final String[] ALGO_NAMES  = { "Greedy", "D&C", "DP+Backtracking" };
    private static final String[] ALGO_LABELS = { "GREEDY", "D&C", "DP+BT" };
    private static final Color[]  ALGO_COLORS = {
        new Color(37,  99, 235),   // blue   — Greedy
        new Color(150, 50, 200),   // purple — D&C
        new Color(0,  150, 100),   // teal   — DP+BT
    };

    // ── Constructor ──────────────────────────────────────────────────────────
    public TracksSwingUI() {
        this.puzzle = TracksPuzzle.demo();
        this.state  = new TracksState(puzzle);

        setTitle("Tracks Puzzle — Player vs CPU");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(C_BG);
        setLayout(new BorderLayout(12, 12));
        getRootPane().setBorder(new EmptyBorder(14, 14, 14, 14));
        getContentPane().setBackground(C_BG);

        int rows = puzzle.rows, cols = puzzle.cols;

        // ── Top bar ──────────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setOpaque(false);

        JLabel title = new JLabel("Tracks Puzzle");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(new Color(30, 30, 90));

        statusLabel = new JLabel("Your turn — click a cell to place track");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        statusLabel.setForeground(new Color(60, 60, 60));

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(statusLabel);
        topBar.add(titleBlock, BorderLayout.WEST);

        // Controls
        JButton newBtn  = styledButton("New Game",   new Color(37,  99, 235));
        undoButton      = styledButton("Undo",        new Color(255, 180, 100));
        hintButton      = styledButton("Hint",        new Color( 255, 200,  0));
        JButton helpBtn = styledButton("How to Play", new Color(120,  180, 255));

        algoCombo = new JComboBox<>(ALGO_NAMES);
        algoCombo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        algoCombo.setForeground(new Color(30, 30, 90));
        algoCombo.setBackground(Color.WHITE);
        algoCombo.setFocusable(false);
        algoCombo.setToolTipText("Select CPU algorithm");
        algoCombo.addActionListener(e -> onAlgoChanged());

        JLabel algoPickLabel = new JLabel("CPU: ");
        algoPickLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        algoPickLabel.setForeground(new Color(30, 30, 90));

        JPanel algoPickPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        algoPickPanel.setOpaque(false);
        algoPickPanel.add(algoPickLabel);
        algoPickPanel.add(algoCombo);

        JPanel ctrlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        ctrlPanel.setOpaque(false);
        ctrlPanel.add(algoPickPanel);
        ctrlPanel.add(newBtn);
        ctrlPanel.add(undoButton);
        ctrlPanel.add(hintButton);
        ctrlPanel.add(helpBtn);
        topBar.add(ctrlPanel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // ── Centre: board ────────────────────────────────────────────────────
        int boardW = cols * (CELL + GAP) + 60;
        int boardH = rows * (CELL + GAP) + 60;
        JPanel board = new JPanel(null);
        board.setPreferredSize(new Dimension(boardW, boardH));
        board.setBackground(C_BG);

        int ox = 30, oy = 30;

        colLabels = new JLabel[cols];
        for (int c = 0; c < cols; c++) {
            JLabel lbl = countLabel(String.valueOf(puzzle.colCounts[c]));
            lbl.setBounds(ox + c * (CELL + GAP), oy - 26, CELL, 22);
            colLabels[c] = lbl;
            board.add(lbl);
        }

        rowLabels = new JLabel[rows];
        for (int r = 0; r < rows; r++) {
            JLabel lbl = countLabel(String.valueOf(puzzle.rowCounts[r]));
            lbl.setBounds(ox + cols * (CELL + GAP) + 6, oy + r * (CELL + GAP), 36, CELL);
            rowLabels[r] = lbl;
            board.add(lbl);
        }

        cells = new JButton[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                JButton btn = new JButton();
                btn.setBounds(ox + c * (CELL + GAP), oy + r * (CELL + GAP), CELL, CELL);
                btn.setFont(new Font("Segoe UI", Font.BOLD, 15));
                btn.setFocusPainted(false);
                btn.setMargin(new Insets(0, 0, 0, 0));
                btn.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));
                btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                final int rr = r, cc = c;
                btn.addActionListener(e -> onCellClick(rr, cc));
                cells[r][c] = btn;
                board.add(btn);
            }
        }

        JScrollPane boardScroll = new JScrollPane(board,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        boardScroll.setBorder(null);
        boardScroll.setBackground(C_BG);
        add(boardScroll, BorderLayout.CENTER);

        // ── Right panel ───────────────────────────────────────────────────────
        JPanel rightPanel = new JPanel(new BorderLayout(0, 10));
        rightPanel.setOpaque(false);
        rightPanel.setPreferredSize(new Dimension(220, 0));

        JPanel algoCard = roundedCard();
        algoCard.setLayout(new BoxLayout(algoCard, BoxLayout.Y_AXIS));
        algoCard.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel algoTitle = new JLabel("CPU Algorithm");
        algoTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        algoTitle.setForeground(new Color(80, 80, 120));

        algoLabel = new JLabel(ALGO_LABELS[0]);
        algoLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        algoLabel.setForeground(ALGO_COLORS[0]);

        moveCountLabel = new JLabel("CPU moves: 0");
        moveCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        moveCountLabel.setForeground(new Color(100, 100, 100));

        JTextArea algoDesc = new JTextArea(getAlgoDescription(0));
        algoDesc.setEditable(false); algoDesc.setOpaque(false);
        algoDesc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        algoDesc.setForeground(new Color(80, 80, 80));
        algoDesc.setLineWrap(true); algoDesc.setWrapStyleWord(true);
        algoDesc.setMaximumSize(new Dimension(200, 200));

        algoCombo.addActionListener(e ->
            algoDesc.setText(getAlgoDescription(algoCombo.getSelectedIndex())));

        algoCard.add(algoTitle);
        algoCard.add(Box.createVerticalStrut(4));
        algoCard.add(algoLabel);
        algoCard.add(Box.createVerticalStrut(2));
        algoCard.add(moveCountLabel);
        algoCard.add(Box.createVerticalStrut(8));
        algoCard.add(new JSeparator());
        algoCard.add(Box.createVerticalStrut(8));
        algoCard.add(algoDesc);
        rightPanel.add(algoCard, BorderLayout.NORTH);

        JPanel logCard = roundedCard();
        logCard.setLayout(new BorderLayout());
        logCard.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel logTitle = new JLabel("Move Log");
        logTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        logTitle.setForeground(new Color(80, 80, 120));
        logCard.add(logTitle, BorderLayout.NORTH);

        logPanel = new JPanel();
        logPanel.setLayout(new BoxLayout(logPanel, BoxLayout.Y_AXIS));
        logPanel.setOpaque(false);

        JScrollPane logScroll = new JScrollPane(logPanel);
        logScroll.setBorder(null);
        logScroll.setOpaque(false);
        logScroll.getViewport().setOpaque(false);
        logCard.add(logScroll, BorderLayout.CENTER);

        rightPanel.add(logCard, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        // ── Wiring ───────────────────────────────────────────────────────────
        newBtn    .addActionListener(e -> resetGame());
        undoButton.addActionListener(e -> doUndo());
        hintButton.addActionListener(e -> doHint());
        helpBtn   .addActionListener(e -> showHelp());

        pack();
        setMinimumSize(new Dimension(780, 580));
        setLocationRelativeTo(null);
        refreshBoard();
    }

    // ── Algorithm combo handler ───────────────────────────────────────────────
    private void onAlgoChanged() {
        int idx = algoCombo.getSelectedIndex();
        // Map combo index directly to enum (order must match)
        state.setCpuAlgorithm(TracksState.CpuAlgorithm.values()[idx]);
        updateAlgoLabel();
    }

    // ── Cell click ────────────────────────────────────────────────────────────
    private void onCellClick(int r, int c) {
        if (gameOver || !playerTurn) return;
        hintPos = null;   // clear hint highlight on any click
        state.cycleCell(r, c);
        refreshBoard();
        addLog("You", r, c, state.getCell(r, c));
        if (checkWin("You")) return;

        playerTurn = false;
        setStatus("CPU thinking…");
        Timer t = new Timer(600, e -> doCpuMove());
        t.setRepeats(false); t.start();
    }

    // ── Hint ──────────────────────────────────────────────────────────────────
    private void doHint() {
        if (gameOver || !playerTurn) return;
        Pos hint = state.getHintCell();
        if (hint == null) {
            setStatus("No hint available right now.");
            return;
        }
        hintPos = hint;
        refreshBoard();
        setStatus("Hint: try cell " + hint);
    }

    // ── Progress status ───────────────────────────────────────────────────────
    private String getProgressStatus() {
        int[] rowCur = state.getRowTrackCounts();
        int[] colCur = state.getColTrackCounts();
        int rowsLeft = 0, colsLeft = 0;
        for (int i = 0; i < puzzle.rows; i++)
            if (rowCur[i] < puzzle.rowCounts[i]) rowsLeft++;
        for (int i = 0; i < puzzle.cols; i++)
            if (colCur[i] < puzzle.colCounts[i]) colsLeft++;
        boolean hasPath = state.connectedPath();
        if (rowsLeft == 0 && colsLeft == 0 && hasPath) return "Path complete and counts satisfied!";
        if (rowsLeft == 0 && colsLeft == 0)             return "All counts done — connect A to B!";
        if (hasPath) return "A→B connected! Still need: " + rowsLeft + " row(s), " + colsLeft + " col(s)";
        return "Place tracks — " + rowsLeft + " row(s) and " + colsLeft + " col(s) still needed";
    }

    // ── CPU move ──────────────────────────────────────────────────────────────
    private void doCpuMove() {
        if (gameOver) return;
        String algo = ALGO_NAMES[algoCombo.getSelectedIndex()];
        try {
            boolean moved = state.cpuMove();
            cpuMoveCount++;
            moveCountLabel.setText("CPU moves: " + cpuMoveCount);

            // Find last TRACK cell placed by CPU (skip endpoints)
            lastCpuPos = state.getLastCpuPlaced();
            if (lastCpuPos == null) {
                // Fallback: scan history
                for (int i = state.moveHistory.size() - 1; i >= 0; i--) {
                    GameMove m = state.moveHistory.get(i);
                    boolean ep = (m.r == puzzle.start.r && m.c == puzzle.start.c)
                              || (m.r == puzzle.end.r   && m.c == puzzle.end.c);
                    if (m.newState == CellState.TRACK && !ep) {
                        lastCpuPos = new Pos(m.r, m.c);
                        break;
                    }
                }
            }

            refreshBoard();

            if (moved && lastCpuPos != null) {
                addLog("CPU (" + algo + ")", lastCpuPos.r, lastCpuPos.c, CellState.TRACK);
                setStatus("CPU [" + algo + "] → " + lastCpuPos + "   Your turn");
            } else {
                addLog("CPU (" + algo + ")", -1, -1, null);
                setStatus("CPU couldn't move — your turn");
            }

            if (checkWin("CPU (" + algo + ")")) return;

        } catch (Exception ex) {
            ex.printStackTrace();
            setStatus("CPU error (" + ex.getMessage() + ") — your turn");
        } finally {
            if (!gameOver) {
                playerTurn = true;
                undoButton.setEnabled(state.canUndo());
            }
        }
    }

    // ── Win check ─────────────────────────────────────────────────────────────
    private boolean checkWin(String who) {
        if (!state.isSolved()) return false;
        gameOver = true; playerTurn = false; lastCpuPos = null; hintPos = null;
        refreshBoard();
        paintWinAnimation();
        String algo = ALGO_NAMES[algoCombo.getSelectedIndex()];
        String msg  = who.startsWith("CPU")
            ? "🤖 CPU solved it!\nAlgorithm: " + algo
            : "🎉 You solved it!";
        setStatus("SOLVED! " + msg.replace("\n", " — "));
        SwingUtilities.invokeLater(() -> {
            int choice = JOptionPane.showConfirmDialog(
                this, msg + "\n\nPlay again?",
                "Puzzle Solved!", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) resetGame();
        });
        return true;
    }

    private void paintWinAnimation() {
        Timer flash = new Timer(120, null);
        final int[] ticks = {0};
        flash.addActionListener(e -> {
            ticks[0]++;
            Color c = (ticks[0] % 2 == 0) ? C_END : C_START;
            for (int r = 0; r < puzzle.rows; r++)
                for (int cc = 0; cc < puzzle.cols; cc++)
                    if (state.getCell(r, cc) == CellState.TRACK)
                        cells[r][cc].setBackground(c);
            if (ticks[0] >= 6) { flash.stop(); refreshBoard(); }
        });
        flash.start();
    }

    // ── Board refresh ─────────────────────────────────────────────────────────
    private void refreshBoard() {
        int[][] grid    = state.toIntGrid();
        Set<Integer> badRows = state.getBadRows();
        Set<Integer> badCols = state.getBadCols();
        int[] curRow = state.getRowTrackCounts();
        int[] curCol = state.getColTrackCounts();

        for (int r = 0; r < puzzle.rows; r++) {
            for (int c = 0; c < puzzle.cols; c++) {
                JButton btn = cells[r][c];
                boolean isStart    = (r == puzzle.start.r && c == puzzle.start.c);
                boolean isEnd      = (r == puzzle.end.r   && c == puzzle.end.c);
                boolean isLastCpu  = lastCpuPos != null && lastCpuPos.r == r && lastCpuPos.c == c;
                boolean isHintCell = state.isHintCell(r, c);
                boolean isHintHigh = hintPos    != null && hintPos.r    == r && hintPos.c    == c;
                int v = grid[r][c];

                btn.setOpaque(true);
                if (isStart) {
                    btn.setText("A"); btn.setFont(new Font("Segoe UI", Font.BOLD, 18));
                    btn.setBackground(C_START); btn.setForeground(Color.BLACK);
                } else if (isEnd) {
                    btn.setText("B"); btn.setFont(new Font("Segoe UI", Font.BOLD, 18));
                    btn.setBackground(C_END); btn.setForeground(Color.BLACK);
                } else if (isHintCell) {
                    // Pre-placed hint — always shown as purple star regardless of game state
                    btn.setText("★"); btn.setFont(new Font("Segoe UI", Font.BOLD, 20));
                    btn.setBackground(C_HINT); btn.setForeground(Color.WHITE);
                } else if (v == CellState.TRACK.toInt()) {
                    btn.setText("■"); btn.setFont(new Font("Segoe UI", Font.BOLD, 22));
                    btn.setBackground(isLastCpu ? C_LAST_CPU : C_TRACK);
                    btn.setForeground(Color.WHITE);
                } else if (v == CellState.BLOCKED.toInt()) {
                    btn.setText("✕"); btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    btn.setBackground(C_BLOCKED); btn.setForeground(new Color(150, 150, 150));
                } else {
                    // EMPTY — may show player hint highlight
                    btn.setText(isHintHigh ? "?" : "");
                    btn.setFont(new Font("Segoe UI", Font.BOLD, 18));
                    btn.setBackground(isHintHigh ? new Color(255, 220, 100) : C_EMPTY);
                    btn.setForeground(new Color(180, 100, 0));
                }

                btn.setBorder(isLastCpu
                    ? BorderFactory.createLineBorder(new Color(200, 80,  0), 3)
                    : isHintCell
                    ? BorderFactory.createLineBorder(new Color(140, 60, 180), 2)
                    : isHintHigh
                    ? BorderFactory.createLineBorder(new Color(200, 160, 0), 2)
                    : BorderFactory.createLineBorder(new Color(200, 200, 210), 1));
            }
        }

        // Row labels: red=over, green=exact, blue=still needed
        for (int r = 0; r < puzzle.rows; r++) {
            rowLabels[r].setText(curRow[r] + "/" + puzzle.rowCounts[r]);
            rowLabels[r].setForeground(
                badRows.contains(r)              ? C_BAD :
                curRow[r] == puzzle.rowCounts[r] ? new Color(20, 150, 20) :
                                                   new Color(50, 50, 100));
        }
        for (int c = 0; c < puzzle.cols; c++) {
            colLabels[c].setText(curCol[c] + "/" + puzzle.colCounts[c]);
            colLabels[c].setForeground(
                badCols.contains(c)              ? C_BAD :
                curCol[c] == puzzle.colCounts[c] ? new Color(20, 150, 20) :
                                                   new Color(50, 50, 100));
        }

        undoButton.setEnabled(state.canUndo() && !gameOver && playerTurn);
        hintButton.setEnabled(!gameOver && playerTurn);
        if (!gameOver && playerTurn) setStatus(getProgressStatus());
        repaint();
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────
    private void doUndo() {
        if (!state.canUndo() || gameOver || !playerTurn) return;
        state.undo();
        lastCpuPos = null; hintPos = null;
        refreshBoard();
        setStatus("Undone — your turn");
    }

    private void resetGame() {
        gameOver = false; playerTurn = true;
        cpuMoveCount = 0; lastCpuPos = null; hintPos = null;
        state.resetToInitialState();
        logPanel.removeAll(); logPanel.revalidate(); logPanel.repaint();
        moveCountLabel.setText("CPU moves: 0");
        setStatus("New game — your turn!");
        refreshBoard();
    }

    private void setStatus(String s) { statusLabel.setText(s); }

    private void updateAlgoLabel() {
        int idx = algoCombo.getSelectedIndex();
        if (idx < 0 || idx >= ALGO_LABELS.length) return;
        algoLabel.setText(ALGO_LABELS[idx]);
        algoLabel.setForeground(ALGO_COLORS[idx]);
    }

    private void addLog(String who, int r, int c, CellState s) {
        String text = (r < 0)
            ? who + ": no move"
            : who + ": (" + r + "," + c + ") → "
              + (s == CellState.TRACK   ? "TRACK"   :
                 s == CellState.BLOCKED ? "BLOCKED" : "EMPTY");
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(who.startsWith("CPU") ? new Color(37, 99, 235) : new Color(20, 120, 20));
        lbl.setBorder(new EmptyBorder(2, 0, 2, 0));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        logPanel.add(lbl, 0);
        logPanel.revalidate();
    }

    private String getAlgoDescription(int idx) {
        switch (idx) {
            case 1: return
                "Divide & Conquer:\n\n" +
                "Splits frontier by seam column into LEFT and RIGHT halves.\n\n" +
                "Conquers each half independently (dist to B + dist to seam).\n\n" +
                "Combines by extending the half with more remaining quota — " +
                "both halves grow evenly toward the seam.\n\n" +
                "⚖️ Balanced growth from both ends.";
            case 2: return
                "DP + Backtracking:\n\n" +
                "Exhaustive backtracking search over all legal placements.\n\n" +
                "DP memoizes board states (BitSet + row/col counts) to avoid " +
                "re-exploring identical configurations.\n\n" +
                "Branch-and-bound prunes paths that can't beat best known cost.\n\n" +
                "✅ Finds the optimal (fewest-step) solution path. " +
                "Commits only the first cell of that path each turn.";
            default: return
                "Greedy:\n\n" +
                "QuickSort frontier by dist to B, then score each cell:\n" +
                "  • Distance to B\n" +
                "  • Degree validity\n" +
                "  • Connectivity to A (+/−)\n" +
                "  • Row + col SLACK bonus\n\n" +
                "🧠 Avoids nearly-full rows. Takes the safest next step.";
        }
    }

    private JLabel countLabel(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(new Color(50, 50, 100));
        return l;
    }

    private JButton styledButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        b.setForeground(Color.BLACK); b.setBackground(bg);
        b.setOpaque(true);
        b.setBorderPainted(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.darker(), 1, true),
            new EmptyBorder(7, 16, 7, 16)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JPanel roundedCard() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(C_PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(220, 220, 230));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }

    private void showHelp() {
        JOptionPane.showMessageDialog(this,
            "<html><body style='width:400px;font-family:Segoe UI'>" +
            "<h2>How to Play</h2>" +
            "<p><b>Goal:</b> Draw a continuous railway track from <b>A</b> to <b>B</b>.</p>" +
            "<p><b>Rules:</b><ul>" +
            "<li>Numbers show required track cells per row/column.</li>" +
            "<li>No branching — every track cell must touch exactly 2 others (endpoints touch 1).</li>" +
            "<li>Click a cell to toggle: Empty ↔ Track.</li>" +
            "<li>The <b>★ purple cell</b> is a <b>fixed hint</b> — the path MUST pass through it. " +
            "It cannot be removed.</li>" +
            "</ul></p>" +
            "<h3>Buttons</h3>" +
            "<p><b>Hint</b> — highlights a suggested next empty cell (yellow ?) on the solution path.</p>" +
            "<p><b>Undo</b> — reverts your last move.</p>" +
            "<h3>CPU Algorithms</h3>" +
            "<p><b>Greedy</b> — scores each frontier cell; avoids nearly-full rows (slack bonus).</p>" +
            "<p><b>D&C</b> — divides board by seam column; conquers each half independently; " +
            "combines by extending the half with more remaining quota.</p>" +
            "<p><b>DP+Backtracking</b> — exhaustive search with memoization; finds the optimal " +
            "solution path and commits its first cell each turn.</p>" +
            "<p>Watch the <b>orange cell</b> (last CPU move) to compare algorithms.</p>" +
            "</body></html>",
            "How to Play", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── Entry point ───────────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new TracksSwingUI().setVisible(true);
        });
    }
}