package mlbench.pagerank;

import mpid.core.HadoopWriter;
import mpid.core.MPI_D;
import mpid.core.MPI_D_Exception;
import mpid.core.util.MPI_D_Constants;
import mpid.util.hadoop.HadoopIOUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PageRank2 {


    static Map<Integer, PageWritable> pages = new ConcurrentHashMap<>();

    public void exec(String[] args, int taskRank) throws MPI_D_Exception, IOException,
            InterruptedException {
        HashMap<String, String> conf = new HashMap<String, String>();
        conf.put(MPI_D_Constants.ReservedKeys.CommonModeKeys.BLOCK_METADATA_PERCENT, "0.45");
        conf.put("sort.enable", "true");
        MPI_D.Init(args, MPI_D.Mode.Iteration, conf, taskRank);

        int size = MPI_D.Comm_size(taskRank, MPI_D.COMM_BIPARTITE_O);
        int rank = MPI_D.Comm_rank(taskRank, MPI_D.COMM_BIPARTITE_O);

        long t1 = System.currentTimeMillis();

        MPI_D.setKVClass(taskRank, IntWritable.class, PageWritable.class, Text.class,
                NullWritable.class);
        MPI_D.setSource(taskRank, new Path(args[0])); // set input
        int iterCnt = Integer.valueOf(args[1]);
        String outDir = args[2];

        IntWritable url = new IntWritable();
        // first stage send the pages
        int recvHandler = MPI_D.makeOneBufferRegion(taskRank);
        String prev = null;
        Object[] kv = MPI_D.Recv(taskRank);
        while (kv != null) {
            if (kv[0].toString().startsWith("#")) {
                kv = MPI_D.Recv(taskRank);
            } else
                break;
        }
        if (kv != null) {
            String ps[] = ((Text) kv[0]).toString().split("\\s+");
            prev = ps[0];
            PageWritable p = new PageWritable(Integer.valueOf(ps[0]));
            p.addLink(Integer.valueOf(ps[1]));

            kv = MPI_D.Recv(taskRank);
            while (kv != null) {
                while (kv != null && kv[0].toString().startsWith("#")) {
                    kv = MPI_D.Recv(taskRank);
                }
                if (kv == null) {
                    break;
                }
                ps = ((Text) kv[0]).toString().split("\\s+");
                if (!prev.equals(ps[0])) {
                    url.set(p.url);
                    MPI_D.Send(taskRank, url, p);
                    // System.out.println(url + " => " + p);
                    prev = ps[0];
                    try {
                        p = new PageWritable(Integer.valueOf(ps[0]));
                    } catch (Exception e) {
                        System.out.println(kv[0]);
                        throw e;
                    }

                }

                p.addLink(Integer.valueOf(ps[1]));

                kv = MPI_D.Recv(taskRank);
            }
            url.set(p.url);
            MPI_D.Send(taskRank, url, p);
            // System.out.println(url + " => " + p);
        }
        MPI_D.stopStage(taskRank);

        // second stage send the pages
        MPI_D.setKVClass(taskRank, IntWritable.class, DoubleWritable.class, IntWritable.class,
                PageWritable.class);
        MPI_D.setSource(taskRank, recvHandler);
        int recvHandler2 = MPI_D.makeOneBufferRegion(taskRank);

        DoubleWritable dval = new DoubleWritable();
        kv = MPI_D.Recv(taskRank);
        while (kv != null) {
            PageWritable page = (PageWritable) kv[1];
            if (pages.containsKey(page.url)) {
                pages.get(page.url).addLinks(page.links);
            } else {
                pages.put(page.url, page.clone());
            }

            double val = page.val / page.links.size();
            for (Integer lk : page.links) {
                url.set(lk);
                dval.set(val);
                MPI_D.Send(taskRank, url, dval);
            }
            kv = MPI_D.Recv(taskRank);
        }

        MPI_D.stopStage(taskRank);
        long loadTime = System.currentTimeMillis() - t1;
        List<Long> times = new ArrayList<>();

        MPI_D.setKVClass(taskRank, IntWritable.class, DoubleWritable.class, IntWritable.class,
                DoubleWritable.class);

        while ((iterCnt--) != 0) {
            t1 = System.currentTimeMillis();
            MPI_D.setSource(taskRank, recvHandler2); // set input
            int recvHandler1 = MPI_D.makeOneBufferRegion(taskRank);

            int prevUrl = 0;
            double val = 0;
            kv = MPI_D.Recv(taskRank);
            if (kv != null) {
                prevUrl = ((IntWritable) kv[0]).get();
            }
            while (kv != null) {
                if (prevUrl != ((IntWritable) kv[0]).get()) {
                    PageWritable p2 = pages.get(prevUrl);
                    if (p2 != null) {
                        p2.val = 0.15 + 0.85 * val;

                        double outVal = p2.val / p2.links.size();
                        for (Integer lk : p2.links) {
                            url.set(lk);
                            dval.set(outVal);

                            MPI_D.Send(taskRank, url, dval);
                        }
                    } else {
                    }
                    val = 0;
                    prevUrl = ((IntWritable) kv[0]).get();
                }
                val += ((DoubleWritable) kv[1]).get();

                kv = MPI_D.Recv(taskRank);
            }
            PageWritable p2 = pages.get(prevUrl);
            if (p2 != null) {
                p2.val = 0.15 + 0.85 * val;
                double outVal = p2.val / p2.links.size();
                for (Integer lk : p2.links) {
                    url.set(lk);
                    dval.set(outVal);
                    MPI_D.Send(taskRank, url, dval);
                }
            }
            MPI_D.stopStage(taskRank);

            times.add(System.currentTimeMillis() - t1);
            MPI_D.releaseRecvBuffer(taskRank, recvHandler2);
            recvHandler2 = recvHandler1;
        }

        t1 = System.currentTimeMillis();
        JobConf jobConf = MPI_D.getContext(taskRank).getJobConf();
        OutputFormat outputFormatClass = jobConf.getOutputFormat();
        Class keyClass = jobConf.getOutputKeyClass();
        Class valueClass = jobConf.getOutputValueClass();
        if (outputFormatClass == null) {
            throw new MPI_D_Exception("Output format class not set");
        }
        if (keyClass == null) {
            throw new MPI_D_Exception("Output key class not set");
        }
        if (valueClass == null) {
            throw new MPI_D_Exception("Output value class not set");
        }

        if (taskRank == 0) {
            HadoopWriter<IntWritable, DoubleWritable> writer = HadoopIOUtil.getNewWriter(
                    jobConf, outDir, IntWritable.class, DoubleWritable.class,
                    TextOutputFormat.class, null, rank, taskRank, MPI_D.COMM_BIPARTITE_A);
//            String name = "part-" + taskRank + "-" + rank;
//            jobConf.setOutputKeyClass(IntWritable.class);
//            jobConf.setOutputValueClass(DoubleWritable.class);
//            jobConf.set("mapred.output.dir", outDir);
//            TaskAttemptContext context = new TaskAttemptContext(jobConf, MPI_D.getContext
// (taskRank)
//                    .getTaskAttemptID());
//
//            TextOutputFormat<IntWritable, DoubleWritable> output = new TextOutputFormat<>();
//
//            FileOutputCommitter fcommitter = (FileOutputCommitter) output
//                    .getOutputCommitter(context);
//            RecordWriter<IntWritable, DoubleWritable> writer = output.getRecordWriter(context);
            IntWritable purl = new IntWritable();
            DoubleWritable value = new DoubleWritable();
            for (PageWritable p1 : pages.values()) {
                purl.set(p1.url);
                value.set(p1.val);
                writer.write(purl, value);
            }
            writer.close();
//            writer.close(context);
//            if (fcommitter.needsTaskCommit(context)) {
//                fcommitter.commitTask(context);
//            }

            if (rank == 0) {
                System.out.println("Iteration 0 is load data, iteration process start from 1.");
            }
        }

        System.out.println(rank + " " + taskRank + " LoadData cost " + loadTime + " ms.");
        for (int i = 0; i < times.size(); i++) {
            System.out.println(rank + " " + taskRank + " Iteration " + i + " cost " + times.get(i)
                    + " ms.");
        }
        System.out.println(rank + " " + taskRank + " Output file cost "
                + (System.currentTimeMillis() - t1) + " ms.");

        MPI_D.Finalize(taskRank);
    }

    public static void run(String[] args, int taskRank) throws MPI_D_Exception, IOException,
            InterruptedException {
        PageRank2 r = new PageRank2();
        r.exec(args, taskRank);
    }
}
