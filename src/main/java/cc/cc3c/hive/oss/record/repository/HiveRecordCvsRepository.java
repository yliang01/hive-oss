
//import java.io.FileNotFoundException;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.util.Arrays;
//import java.util.TreeMap;
//
//@Slf4j
//@Component
//public class HiveRecordRepository {
//
//    @Value("${hive.repository.aofFile}")
//    private String aofFile;
//    @Value("${hive.repository.indexFile}")
//    private String indexFile;
//
//    private final TreeMap<Integer, Record> recordMap = new TreeMap<>();
//    private CsvReader aofReader = null;
//    private CsvWriter aofWriter = null;
//    private CsvWriter indexWriter = null;
//
//    @EventListener(ApplicationReadyEvent.class)
//    public synchronized void init() {
//        try {
//            aofReader = new CsvReader(aofFile, ',', StandardCharsets.UTF_8);
//        } catch (FileNotFoundException e) {
//            log.error("failed to init read aof file", e);
//            System.exit(1);
//        }
//        try {
//            FileWriter fileWriter = new FileWriter(aofFile, StandardCharsets.UTF_8, true);
//            aofWriter = new CsvWriter(fileWriter, ',');
//            aofWriter.setForceQualifier(true);
//        } catch (IOException e) {
//            log.error("failed to init write aof file", e);
//            System.exit(1);
//        }
//        try {
//            FileWriter fileWriter = new FileWriter(indexFile, StandardCharsets.UTF_8, false);
//            indexWriter = new CsvWriter(fileWriter, ',');
//            indexWriter.setForceQualifier(true);
//        } catch (IOException e) {
//            log.error("failed to init write aof file", e);
//            System.exit(1);
//        }
//
//        try {
//            while (aofReader.readRecord()) {
//                String[] values = aofReader.getValues();
//                if ("put".equals(values[1])) {
//                    Record record = new Record();
//                    record.setId(Integer.parseInt(values[0]));
//                    record.setFileName(values[2]);
//                    record.setKey(values[3]);
//                    record.setZipped(Boolean.parseBoolean(values[4]));
//                    record.setSource(values[5]);
//                    record.setSize(StringUtils.isNotEmpty(values[6]) ? Long.parseLong(values[6]) : 0);
//                    record.setUpdate_time(values[7]);
//                    recordMap.put(record.getId(), record);
//                } else if ("remove".equals(values[1])) {
//                    recordMap.remove(Integer.parseInt(values[0]));
//                } else {
//                    log.error("incorrect record {}", Arrays.toString(values));
//                }
//            }
//            log.info("Total record read {}", recordMap.size());
//        } catch (Exception e) {
//            log.error("failed to read aof file", e);
//            System.exit(1);
//        }
//
//        try {
//            recordMap.forEach((key, value) -> {
//                String[] data = new String[8];
//                data[0] = String.valueOf(value.getId());
//                data[1] = "put";
//                data[2] = value.getFileName();
//                data[3] = value.getKey();
//                data[4] = String.valueOf(value.isZipped());
//                data[5] = value.getSource();
//                data[6] = String.valueOf(value.getSize());
//                data[7] = value.getUpdate_time();
//                try {
//                    indexWriter.writeRecord(data);
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//            });
//            log.info("Total record generate in index.dat {}", recordMap.size());
//        } catch (Exception e) {
//            log.error("failed to generate index file", e);
//            System.exit(1);
//        } finally {
//            indexWriter.flush();
//            indexWriter.close();
//        }
//
////        Record test = new Record();
////        test.setFileName("filename");
////        test.setKey("key");
////        test.setZipped(true);
////        test.setSource("source");
////        test.setSize(999999999);
////        test.setUpdate_time("time");
////        put(test);
//    }
//
//    public synchronized void put(Record record) {
//        try {
//            record.setId(recordMap.lastKey() + 1);
//
//            String[] data = new String[8];
//            data[0] = String.valueOf(record.getId());
//            data[1] = "put";
//            data[2] = record.getFileName();
//            data[3] = record.getKey();
//            data[4] = String.valueOf(record.isZipped());
//            data[5] = record.getSource();
//            data[6] = String.valueOf(record.getSize());
//            data[7] = record.getUpdate_time();
//            aofWriter.writeRecord(data);
//            aofWriter.flush();
//
//            recordMap.put(record.getId(), record);
//        } catch (IOException e) {
//            log.error("put failed", e);
//        }
//    }
//
//    public synchronized void remove(Record record) {
//        try {
//            String[] data = new String[8];
//            data[0] = String.valueOf(record.getId());
//            data[1] = "remove";
//            aofWriter.writeRecord(data);
//            aofWriter.flush();
//
//            recordMap.remove(record.getId());
//        } catch (IOException e) {
//            log.error("remove failed", e);
//        }
//    }
//
//    public synchronized Record get(Integer id) {
//        return recordMap.get(id);
//    }
//}
