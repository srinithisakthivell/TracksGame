import java.util.*;

public class GraphNode {
	public final Pos position;
	public CellState state;
	public final Set<GraphNode> neighbors = new HashSet<>();

	public GraphNode(Pos position, CellState state) {
		this.position = position;
		this.state = state;
	}

	public void addNeighbor(GraphNode node) {
		neighbors.add(node);
	}

	/** Count of adjacent cells currently marked TRACK */
	public int getDegree() {
		int d = 0;
		for (GraphNode n : neighbors)
			if (n.state == CellState.TRACK)
				d++;
		return d;
	}

	public Set<GraphNode> getTrackNeighbors() {
		Set<GraphNode> s = new HashSet<>();
		for (GraphNode n : neighbors)
			if (n.state == CellState.TRACK)
				s.add(n);
		return s;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof GraphNode && position.equals(((GraphNode) o).position);
	}

	@Override
	public int hashCode() {
		return position.hashCode();
	}

	@Override
	public String toString() {
		return "Node" + position + ":" + state;
	}
}
