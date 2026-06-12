import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class TodoApp {

    static final String RESET   = "\033[0m";
    static final String BOLD    = "\033[1m";
    static final String DIM     = "\033[2m";
    static final String RED     = "\033[91m";
    static final String GREEN   = "\033[92m";
    static final String YELLOW  = "\033[93m";
    static final String BLUE    = "\033[94m";
    static final String MAGENTA = "\033[95m";
    static final String CYAN    = "\033[96m";
    static final String WHITE   = "\033[97m";
    static final String GRAY    = "\033[90m";

    static String c(String text, String... codes) {
        StringBuilder sb = new StringBuilder();
        for (String code : codes) sb.append(code);
        sb.append(text).append(RESET);
        return sb.toString();
    }

    static class Task {
        int id; String title; boolean done; String due; String created;
        Task(int id, String title, boolean done, String due, String created) {
            this.id=id; this.title=title; this.done=done; this.due=due; this.created=created;
        }
    }

    static final String DATE_FMT = "yyyy-MM-dd";
    static final String DATA_FILE = System.getProperty("user.dir") + File.separator + "tasks.json";

    static List<Task> loadTasks() {
        List<Task> tasks = new ArrayList<>();
        File f = new File(DATA_FILE);
        if (!f.exists()) return tasks;
        try {
            String raw = new String(Files.readAllBytes(f.toPath())).trim();
            if (raw.isEmpty() || raw.equals("[]")) return tasks;
            raw = raw.substring(raw.indexOf('[')+1, raw.lastIndexOf(']')).trim();
            if (raw.isEmpty()) return tasks;
            for (String obj : splitObjects(raw)) { Task t = parseTask(obj); if (t!=null) tasks.add(t); }
        } catch (IOException e) { System.err.println("Error: " + e.getMessage()); }
        return tasks;
    }

    static List<String> splitObjects(String s) {
        List<String> r = new ArrayList<>(); int depth=0, start=0;
        for (int i=0; i<s.length(); i++) {
            char ch=s.charAt(i);
            if (ch=='{') { if (depth==0) start=i; depth++; }
            else if (ch=='}') { depth--; if (depth==0) r.add(s.substring(start,i+1)); }
        }
        return r;
    }

    static Task parseTask(String obj) {
        try {
            return new Task(Integer.parseInt(jsonField(obj,"id")), jsonField(obj,"title"),
                Boolean.parseBoolean(jsonField(obj,"done")), jsonFieldNullable(obj,"due"), jsonField(obj,"created"));
        } catch (Exception e) { return null; }
    }

    static String jsonField(String obj, String key) {
        String search = "\""+key+"\"";
        int ki = obj.indexOf(search); if (ki<0) return "";
        int start = obj.indexOf(':',ki+search.length())+1;
        while (start<obj.length() && obj.charAt(start)==' ') start++;
        if (obj.charAt(start)=='"') {
            int end = obj.indexOf('"',start+1);
            while (end>0 && obj.charAt(end-1)=='\\') end=obj.indexOf('"',end+1);
            return obj.substring(start+1,end).replace("\\\"","\"").replace("\\\\","\\");
        }
        int end=start;
        while (end<obj.length() && obj.charAt(end)!=',' && obj.charAt(end)!='}') end++;
        return obj.substring(start,end).trim();
    }

    static String jsonFieldNullable(String obj, String key) {
        String v = jsonField(obj,key);
        return (v==null||v.isEmpty()||v.equals("null")) ? null : v;
    }

    static void saveTasks(List<Task> tasks) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i=0; i<tasks.size(); i++) {
            Task t = tasks.get(i);
            sb.append("  {\n    \"id\": ").append(t.id)
              .append(",\n    \"title\": \"").append(t.title.replace("\\","\\\\").replace("\"","\\\""))
              .append("\",\n    \"done\": ").append(t.done)
              .append(",\n    \"due\": ").append(t.due==null?"null":"\""+t.due+"\"")
              .append(",\n    \"created\": \"").append(t.created).append("\"\n  }");
            if (i<tasks.size()-1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        try { Files.write(Paths.get(DATA_FILE), sb.toString().getBytes()); }
        catch (IOException e) { System.err.println("Save error: "+e.getMessage()); }
    }

    static int nextId(List<Task> tasks) {
        return tasks.stream().mapToInt(t->t.id).max().orElse(0)+1;
    }

    static String dueLabel(String due, boolean done) {
        if (due==null||due.isEmpty()) return "";
        try {
            LocalDate d = LocalDate.parse(due, DateTimeFormatter.ofPattern(DATE_FMT));
            LocalDate today = LocalDate.now();
            if (done) return c(" due "+due, GRAY);
            long diff = ChronoUnit.DAYS.between(today,d);
            if (diff<0)  return c(" ⚠ OVERDUE "+Math.abs(diff)+"d ("+due+")", RED,BOLD);
            if (diff==0) return c(" ⏰ due TODAY", YELLOW,BOLD);
            return c(" due "+due+" (in "+diff+"d)", CYAN);
        } catch (DateTimeParseException e) { return ""; }
    }

    static void printTask(Task t) {
        System.out.println("  "+c(String.format("[#%3d]",t.id),BLUE)+" "+
            (t.done?c("✓",GREEN,BOLD):c("○",GRAY))+"  "+
            (t.done?c(t.title,DIM):c(t.title,WHITE,BOLD))+
            dueLabel(t.due,t.done)+c("  created "+t.created,GRAY));
    }

    static void cmdAdd(String title, String due) {
        List<Task> tasks = loadTasks();
        Task t = new Task(nextId(tasks),title,false,due,
            LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FMT)));
        tasks.add(t); saveTasks(tasks);
        System.out.println(c("  ✚ Task added:",GREEN)+"  #"+t.id+" — "+t.title);
    }

    static void cmdList(String filter) {
        List<Task> tasks = loadTasks();
        List<Task> subset; String label;
        switch(filter) {
            case "pending":   subset=tasks.stream().filter(t->!t.done).collect(Collectors.toList()); label="Pending tasks"; break;
            case "completed": subset=tasks.stream().filter(t->t.done).collect(Collectors.toList());  label="Completed tasks"; break;
            default: subset=tasks; label="All tasks";
        }
        long done = tasks.stream().filter(t->t.done).count();
        System.out.println(c("\n  📋 "+label,MAGENTA,BOLD));
        System.out.println(c("  "+done+"/"+tasks.size()+" complete",GRAY));
        System.out.println();
        if (subset.isEmpty()) System.out.println(c("  (no tasks)",GRAY));
        else subset.forEach(TodoApp::printTask);
        System.out.println();
    }

    static void cmdComplete(int id) {
        List<Task> tasks=loadTasks();
        for (Task t:tasks) if (t.id==id) { t.done=true; saveTasks(tasks);
            System.out.println(c("  ✓ Marked complete:",GREEN)); printTask(t); return; }
        System.out.println(c("  ✗ No task with id #"+id,RED));
    }

    static void cmdUncomplete(int id) {
        List<Task> tasks=loadTasks();
        for (Task t:tasks) if (t.id==id) { t.done=false; saveTasks(tasks);
            System.out.println(c("  ↩ Marked pending:",YELLOW)); printTask(t); return; }
        System.out.println(c("  ✗ No task with id #"+id,RED));
    }

    static void cmdUpdate(int id, String newTitle, String newDue, boolean clearDue) {
        List<Task> tasks=loadTasks();
        for (Task t:tasks) if (t.id==id) {
            if (newTitle!=null) t.title=newTitle;
            if (newDue!=null)   t.due=newDue;
            if (clearDue)       t.due=null;
            saveTasks(tasks); System.out.println(c("  ✎ Task updated:",YELLOW)); printTask(t); return;
        }
        System.out.println(c("  ✗ No task with id #"+id,RED));
    }

    static void cmdDelete(int id) {
        List<Task> tasks=loadTasks(); int before=tasks.size();
        tasks.removeIf(t->t.id==id);
        if (tasks.size()==before) { System.out.println(c("  ✗ No task with id #"+id,RED)); return; }
        saveTasks(tasks); System.out.println(c("  🗑 Deleted task #"+id,RED));
    }

    static void cmdClear() throws IOException {
        System.out.print(c("  Delete ALL tasks? (yes/no): ",YELLOW));
        BufferedReader br=new BufferedReader(new InputStreamReader(System.in));
        String ans=br.readLine();
        if ("yes".equalsIgnoreCase(ans!=null?ans.trim():"")) {
            saveTasks(new ArrayList<>()); System.out.println(c("  All tasks deleted.",RED));
        } else System.out.println(c("  Aborted.",GRAY));
    }

    static String getArg(String[] args, String flag) {
        for (int i=0;i<args.length-1;i++) if (args[i].equals(flag)) return args[i+1]; return null;
    }
    static boolean hasFlag(String[] args, String flag) {
        for (String a:args) if (a.equals(flag)) return true; return false;
    }
    static boolean validDate(String s) {
        try { LocalDate.parse(s,DateTimeFormatter.ofPattern(DATE_FMT)); return true; }
        catch(DateTimeParseException e) { return false; }
    }

    public static void main(String[] args) throws IOException {
        if (args.length==0) { cmdList("all"); return; }
        switch(args[0].toLowerCase()) {
            case "add":
                if (args.length<2) { System.out.println(c("  Usage: add \"<title>\" [--due YYYY-MM-DD]",RED)); return; }
                String due=getArg(args,"--due");
                if (due!=null && !validDate(due)) { System.out.println(c("  Invalid date. Use YYYY-MM-DD",RED)); return; }
                cmdAdd(args[1],due); break;
            case "list":
                String f=getArg(args,"--filter"); if(f==null) f="all";
                if (!f.equals("all")&&!f.equals("pending")&&!f.equals("completed")) {
                    System.out.println(c("  --filter: all | pending | completed",RED)); return; }
                cmdList(f); break;
            case "complete":
                if (args.length<2) { System
