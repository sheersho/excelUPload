package org.example;

public record ImportResult(int filesProcessed, int rowsRead, int rowsInserted, int rowsSkipped) {

	public static ImportResult empty() {
		return new ImportResult(0, 0, 0, 0);
	}

	public ImportResult add(ImportResult other) {
		return new ImportResult(
				filesProcessed + other.filesProcessed,
				rowsRead + other.rowsRead,
				rowsInserted + other.rowsInserted,
				rowsSkipped + other.rowsSkipped
		);
	}
}

