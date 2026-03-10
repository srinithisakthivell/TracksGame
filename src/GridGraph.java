import java.util.*;

public class GridGraph {
	private final int rows, cols;
	private final Map<Pos, GraphNode> nodes = new HashMap<>();
	private final Set<Edge> allEdges = new HashSet<>();

	public GridGraph(int rows, int cols) {
		this.rows = rows;
		this.cols = cols;
		buildGraph();
	}

	private void buildGraph() {
		for (int r = 0; r < rows; r++)
			for (int c = 0; c < cols; c++)
				nodes.put(new Pos(r, c), new GraphNode(new Pos(r, c), CellState.EMPTY));

		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				GraphNode cur = nodes.get(new Pos(r, c));
				if (c + 1 < cols) {
					GraphNode right = nodes.get(new Pos(r, c + 1));
					cur.addNeighbor(right);
					right.addNeighbor(cur);
					allEdges.add(new Edge(new Pos(r, c), new Pos(r, c + 1)));
				}
				if (r + 1 < rows) {
					GraphNode down = nodes.get(new Pos(r + 1, c));
					cur.addNeighbor(down);
					down.addNeighbor(cur);
					allEdges.add(new Edge(new Pos(r, c), new Pos(r + 1, c)));
				}
			}
		}
	}

	public GraphNode getNode(int r, int c) {
		return nodes.get(new Pos(r, c));
	}

	public GraphNode getNode(Pos pos) {
		return nodes.get(pos);
	}

	public int getRows() {
		return rows;
	}

	public int getCols() {
		return cols;
	}

	public Collection<GraphNode> getAllNodes() {
		return nodes.values();
	}

	public Set<GraphNode> getTrackNodes() {
		Set<GraphNode> s = new HashSet<>();
		for (GraphNode n : nodes.values())
			if (n.state == CellState.TRACK)
				s.add(n);
		return s;
	}

	public Set<Edge> getActiveEdges() {
		Set<Edge> s = new HashSet<>();
		for (Edge e : allEdges) {
			GraphNode a = nodes.get(e.from), b = nodes.get(e.to);
			if (a != null && b != null && a.state == CellState.TRACK && b.state == CellState.TRACK)
				s.add(e);
		}
		return s;
	}
}
