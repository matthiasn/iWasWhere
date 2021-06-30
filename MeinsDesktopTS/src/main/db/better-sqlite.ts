import Database from 'better-sqlite3'

const DB_PATH = '/tmp/meinsTS/meins.db'
const db = new Database(DB_PATH, {verbose: console.log})
//const db = new Database(DB_PATH)

export type TabSearchParams = {
  limit: number
  offset: number
  search: string
}

const SELECT_ENTRIES =
  'SELECT * FROM entries WHERE entry_json LIKE $search ORDER BY timestamp DESC LIMIT $limit OFFSET $offset'

export function dbTabSearch(params: TabSearchParams) {
  return db.prepare(SELECT_ENTRIES).all(params)
}

export type InsertEntryParams = {
  timestamp: number
  entryJson: string
}

const INSERT_ENTRY = 'INSERT INTO entries VALUES ($timestamp, $entryJson)'

export function insertEntry(params: InsertEntryParams) {
  return db.prepare(INSERT_ENTRY).run(params)
}
