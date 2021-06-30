import {Connection, ConnectionOptions, createConnection} from 'typeorm'
import {ORMEntry} from './entities/entry'

const DB_PATH = '/tmp/meinsTS/meins.db'
const options: ConnectionOptions = {
  type: 'sqlite',
  database: DB_PATH,
  entities: [ORMEntry],
  synchronize: true,
  logging: ['warn', 'error'],
}
const connection = createConnection(options)

export async function dbConnection(): Promise<Connection> {
  return connection
}
