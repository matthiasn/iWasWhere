// @ts-ignore
import edn from 'edn-to-js'
import log from 'loglevel'
import {asyncForEach} from './util'
import fs from 'fs'
import {dbConnection} from '../db'
import {ORMEntry} from '../db/entities/entry'
import path from 'path'
import os from 'os'
import {insertEntry} from '../db/better-sqlite'

let n = 0
const directoryPath = '/tmp/daily-logs'
const fileRegex = /\d{4}-\d{2}-\d{2}.jrn/g

export function listJrnFiles() {
  return fs.readdirSync(directoryPath).filter((s) => s.match(fileRegex))
}

export async function processFile(fileName: string) {
  async function entryProcessor(line: string) {
    if (line.length === 0) return

    let parsed
    try {
      n += 1
      const db = await dbConnection()
      parsed = edn(line)
      const entry = new ORMEntry()
      const entryJson = JSON.stringify(parsed)
      entry.entryJson = entryJson
      entry.timestamp = parsed.timestamp
      const dbRes = await db.getRepository(ORMEntry).insert(entry)
      //insertEntry({timestamp: parsed.timestamp, entryJson})
      if (n % 10000 === 0) {
        log.info('entryProcessor', fileName, n)
      }
    } catch (e) {
      log.error('entryProcessor', fileName, e.message)
      log.error('line', line)
      log.error('parsed', parsed)
    }
  }

  const filePath = path.join(directoryPath, fileName)
  const lines: string[] = fs.readFileSync(filePath).toString().split('\n')
  await asyncForEach(lines, entryProcessor)
}

export async function importEdn(): Promise<boolean> {
  const files = listJrnFiles()
  log.info(files)
  await asyncForEach(files, processFile)

  return true
}
