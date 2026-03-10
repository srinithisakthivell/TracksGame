public class Pos {
	public final int r, c;

	public Pos(int r, int c) {
		this.r = r;
		this.c = c;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Pos && ((Pos) o).r == r && ((Pos) o).c == c;
	}

	@Override
	public int hashCode() {
		return r * 1000 + c;
	}

	@Override
	public String toString() {
		return "(" + r + "," + c + ")";
	}
}
