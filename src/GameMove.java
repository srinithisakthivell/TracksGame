class GameMove {
	final int r, c;
	final CellState prevState, newState;

	GameMove(int r, int c, CellState prev, CellState next) {
		this.r = r;
		this.c = c;
		this.prevState = prev;
		this.newState = next;
	}
}
