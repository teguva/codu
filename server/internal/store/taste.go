package store

import "time"

type TasteRow struct {
	ID          string
	Fingerprint string
	Titles      int
	Payload     string
	UpdatedAt   int64
}

func (s *Store) GetTasteProfile(id string) (TasteRow, error) {
	var row TasteRow
	err := s.db.QueryRow(
		`SELECT id, fingerprint, titles, payload, updated_at FROM taste_profile WHERE id = ?`,
		id,
	).Scan(&row.ID, &row.Fingerprint, &row.Titles, &row.Payload, &row.UpdatedAt)
	if err != nil {
		return TasteRow{}, err
	}
	return row, nil
}

func (s *Store) PutTasteProfile(row TasteRow) error {
	if row.ID == "" {
		row.ID = "household"
	}
	if row.UpdatedAt == 0 {
		row.UpdatedAt = time.Now().Unix()
	}
	_, err := s.db.Exec(`
INSERT INTO taste_profile (id, fingerprint, titles, payload, updated_at)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT(id) DO UPDATE SET
  fingerprint = excluded.fingerprint,
  titles = excluded.titles,
  payload = excluded.payload,
  updated_at = excluded.updated_at
`, row.ID, row.Fingerprint, row.Titles, row.Payload, row.UpdatedAt)
	return err
}
