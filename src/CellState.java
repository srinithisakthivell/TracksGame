public enum CellState {
	EMPTY(0), TRACK(1), BLOCKED(2);

	public final int value;

	CellState(int value) {
		this.value = value;
	}

	public int toInt() {
		return value;
	}
}
