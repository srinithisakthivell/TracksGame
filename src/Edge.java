public class Edge {
	public final Pos from, to;

	public Edge(Pos from, Pos to) {
		this.from = from;
		this.to = to;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Edge))
			return false;
		Edge e = (Edge) o;
		return (from.equals(e.from) && to.equals(e.to)) || (from.equals(e.to) && to.equals(e.from));
	}

	@Override
	public int hashCode() {
		return from.hashCode() ^ to.hashCode();
	}

	@Override
	public String toString() {
		return from + " -- " + to;
	}
}
