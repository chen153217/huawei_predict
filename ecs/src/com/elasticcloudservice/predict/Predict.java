package com.elasticcloudservice.predict;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Predict {

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    /**
     *
     * @param ecsContent  各个规格的虚拟机的历史请求数据
     * @param inputContent 输入文件中的数据
     * @return  返回最终预测的结果
     */
    public static String[] predictVm(String[] ecsContent, String[] inputContent) {

        /** =========do your work here========== **/

        List<String> resultContents=new LinkedList<String>();//返回预测的结果
        List<Integer> flavorID = new ArrayList<Integer>(ecsContent.length);//将ecsContent中每行的flavorID保存下来
        List<String> createDate = new ArrayList<String>(ecsContent.length);//记录 escContent中每行的日期
        List[] flavorHistory=null;//记录15种虚拟机规格，每种虚拟机规格一段时间内的请求情况存放在对应的List中
        Map<Integer,Integer> flavorFuture;

        int[] physicsInfo = new int[3];//物理服务器资源信息，包含CPU核数，内存以及硬盘大小
        int[] flavorsToPredict;//需要预测的虚拟机型号
        //int[][] flavorsInfo;//各个虚拟机的规格
        int predictDateLength=0;//需要预测的时间长度(以天为单位）
        String referenceItem;//放置策略的参考因素，CPU or MEM

        //对历史请求数据escContent进行处理，获取flavorID,createDate和flavors
        String flavorName;
        String createTime;
        String[] array;
        for (int i = 1; i < ecsContent.length; i++) {

            if (ecsContent[i].contains("\t")
                    && ecsContent[i].split("\t").length == 3) {
                array = ecsContent[i].split("\t");
                flavorName = array[1];
                createTime = array[2];
                if (Integer.valueOf(flavorName.substring(6)) <= 15) {
                    flavorID.add(Integer.valueOf(flavorName.substring(6)));
                    createDate.add(createTime.split("\\s+")[0]);
                }
            }
        }
        try {
            flavorHistory = historyData(flavorID, createDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        //对输入文件中的内容进行处理,提取出physicsInfo，flavorsToPredict，predictDateLength和referenceItem
        String str = inputContent[0];//
        String[] strs = str.split("\\s+");
        for (int j = 0; j < 3; j++)
            physicsInfo[j] = Integer.valueOf(strs[j]);

        int flavorNum = Integer.valueOf(inputContent[2]);
        flavorsToPredict = new int[flavorNum];
       // flavorsInfo = new int[flavorNum][3];
       int[][] flavorsInfo={
                {1,1024},
                {1,2048},
                {1,4096},
                {2,2048},
                {2,4096},
                {2,8129},
                {4,4096},
                {4,8129},
                {4,16384},
                {8,8192},
                {8,16384},
                {8,32768},
                {16,16384},
                {16,32768},
                {16,65536}
        };

        for (int j = 0; j < flavorNum; j++) {
            str = inputContent[j + 3];
            strs = str.split("\\s+");
            flavorsToPredict[j] =Integer.valueOf(strs[0].substring(6));
  //          flavorsToPredict[j] = flavorsInfo[j][0] = Integer.valueOf(strs[0].substring(6));
 //           flavorsInfo[j][1] = Integer.valueOf(strs[1]);
//            flavorsInfo[j][2] = Integer.valueOf(strs[2]);
        }

        referenceItem = inputContent[4 + flavorNum];

        try {
            Date beginDate = sdf.parse(inputContent[6 + flavorNum].split("\\s+")[0]);
            Date endDate = sdf.parse(inputContent[7 + flavorNum].split("\\s+")[0]);
            predictDateLength = (int) ((endDate.getTime() - beginDate.getTime()) / (1000 * 60 * 60 * 24));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        flavorFuture=predictFlavor(flavorHistory,flavorsToPredict,predictDateLength);
        int predictFlavorNum=0;
        for(Map.Entry<Integer,Integer> map:flavorFuture.entrySet()){
            predictFlavorNum+=map.getValue();
        }
        resultContents.add(String.valueOf(predictFlavorNum));
        for(Map.Entry<Integer,Integer> map:flavorFuture.entrySet()){
            resultContents.add("flavor"+map.getKey()+" "+map.getValue());
        }
        resultContents.add("\r\n");

        List<Map<Integer,Integer>> physicsList=putFlavorsToPhysics(flavorFuture,flavorsInfo,physicsInfo,referenceItem);
        resultContents.add(String.valueOf(physicsList.size()));
        int num=1;
        String s="";
        for(Map<Integer,Integer> perPhysics:physicsList){
            s+=num+" ";
            for(Map.Entry<Integer,Integer> perFlavor:perPhysics.entrySet()){
               s+="flavor"+perFlavor.getKey()+" "+perFlavor.getValue()+" ";
            }
            resultContents.add(s);
        }

        return resultContents.toArray(new String[resultContents.size()]);
    }

    /**
     *
     * @param flavorID  历史请求数据每行的虚拟机ID
     * @param createDate　历史请求数据每行中的请求时间
     * @return      处理后的各种规格的flavors的历史请求数据，放到List数组中，每行对应一种规格
     * @throws ParseException
     */
    private static List[] historyData(List<Integer> flavorID, List<String> createDate) throws ParseException {

        List<Integer>[] flavors = new ArrayList[15];//15种虚拟机规格
        for (int i = 0; i < 15; i++)
            flavors[i] = new ArrayList<Integer>();//每种虚拟机规格一段时间内的请求情况存放在对应的List中

        int size = flavorID.size();
        Date beginDate = sdf.parse(createDate.get(0));
        Date endDate = sdf.parse(createDate.get(size - 1));
        Date currentDate;
        int betweenDate = (int) ((endDate.getTime() - beginDate.getTime()) / (1000 * 60 * 60 * 24));
        int benginNum=betweenDate%7;
        int value = 0;

        for (int i = 0; i < betweenDate / 7; i++) {
            for (int j = 0; j < 15; j++) {
                flavors[j].add(0);
            }
        }
        for (int i = 0; i < size; i++) {
            currentDate = sdf.parse(createDate.get(i));
            betweenDate = (int) ((currentDate.getTime() - beginDate.getTime()) / (1000 * 60 * 60 * 24))-benginNum-1;//保证从开始记录的那天到endDate正好是7的倍数
            if (betweenDate >= 0) {
                value = flavors[flavorID.get(i) - 1].get(betweenDate / 7);
                flavors[flavorID.get(i) - 1].set(betweenDate / 7, 1 + value);
            }
        }
        return flavors;
    }

    /**
     *
     * @param historyFlavors  处理后的各种规格的flavors的历史请求数据
     * @param flavorsID       需要预测的虚拟机的ID
     * @param betweenDate     需要预测的时间跨度
     * @return     map中的key-value分别对应flavorID-num,也即未来一段时间需要预测的各种ID的需求量
     */
    private static Map<Integer, Integer> predictFlavor(List<Integer>[] historyFlavors, int[] flavorsID, int betweenDate){
        Map<Integer, Integer> predictCount = new HashMap<>();
        for(int id : flavorsID) {
            int count = predictFlavor(historyFlavors[id - 1], betweenDate);
            predictCount.put(id, count);
        }
        return predictCount;
    }


    /**
     *
     * @param list   一种虚拟机的历史请求记录
     * @param betweenDate  需要预测的时间跨度
     * @return  预测的虚拟机的数目
     */
    private static int predictFlavor(List<Integer> list, int betweenDate) {
        int n = list.size();
        float[] w = new float[n];  //每一周的权重
        w[n - 1] = 0.6f;  //最接近的一个日期分配为0.6权重,取0.6主要是防止出现只有两周时出现0.5  0.5的权重比例
        float lest = 0.4f;
        for (int i = n - 2; i > 0; i--) {
            w[i] = lest * 0.6f;
            lest -= w[i];  //每一次分配过后权重还剩余多少
        }
        w[0] = lest;  //最后剩余的全部分配给w[0];
        float flavorCount = 0;
        for (int i = 0; i < n; i++) {
            flavorCount += w[i] * list.get(i);
        }
        return (int) flavorCount;
    }


    /**
     *
     * @param flavors  要求预测的各种虚拟机的使用需求量
     * @param flavorsInfo  每种虚拟机对应的参数，包括ID，CPU及内存的容量配置
     * @param physicsInfo　　一台物理机对应的配置容量信息，包含CPU核数，内存以及硬盘大小
     * @param referenceItem　CPU或者MEM，放置时优先考虑的因素
     * @return  　一个Map对应一台物理机放置的虚拟机的规格及数量
     */
    private static List<Map<Integer,Integer>> putFlavorsToPhysics(Map<Integer,Integer> flavors,int[][] flavorsInfo,int[] physicsInfo,String referenceItem){
        int[] flavorID=new int[15];   //记录虚拟机ID
        for(int i=0;i<15;i++)
            flavorID[i]=i+1;
        //如果是考虑内存优先，则对内存进行排序
        if(referenceItem.equals("MEM")){
            //随着虚拟机ID增大内存大致也是上升的，用冒泡排序交换次数较少
            for(int i=0;i<15;i++){
                for(int j=i+1;j<15;j++){
                    if(flavorsInfo[i][1]>flavorsInfo[j][1]){
                        int temp1=flavorsInfo[i][1];
                        int temp2=flavorsInfo[j][0];
                        int temp3=flavorID[i];
                        flavorsInfo[i][1]=flavorsInfo[j][1];
                        flavorsInfo[i][0]=flavorsInfo[j][0];
                        flavorID[i]=flavorID[j];
                        flavorsInfo[j][1]=temp1;
                        flavorsInfo[j][0]=temp2;
                        flavorID[j]=temp3;
                    }
                }
            }
        }
        //如果是考虑CPU优先，则对CPU核数多少进行排序
        if(referenceItem.equals("CPU")){
            for(int i=0;i<15;i++){
                for(int j=i+1;j<15;j++){
                    if(flavorsInfo[i][0]>flavorsInfo[j][0]){
                        int temp1=flavorsInfo[i][1];
                        int temp2=flavorsInfo[j][0];
                        int temp3=flavorID[i];
                        flavorsInfo[i][1]=flavorsInfo[j][1];
                        flavorsInfo[i][0]=flavorsInfo[j][0];
                        flavorID[i]=flavorID[j];
                        flavorsInfo[j][1]=temp1;
                        flavorsInfo[j][0]=temp2;
                        flavorID[j]=temp3;
                    }
                }
            }
        }
        int currentCPU=physicsInfo[0];  //目前正在部署的服务器剩余的CPU核数
        int currentMEM=physicsInfo[1]*1024;   //目前正在部署的服务器剩余的内存
        int count=0;                     //记录各种虚拟机加起来的总数量
        List<Map<Integer, Integer>> serverList=new ArrayList<Map<Integer,Integer>>();  //存放最终结果
        int[] currentFlavorCount=new int[15];   //用来记录每种虚拟机的数量

        //遍历Map记录各规格的虚拟机数量
        for(Map.Entry<Integer,Integer> aKindOfFlavor:flavors.entrySet()){
            currentFlavorCount[aKindOfFlavor.getKey()-1]=aKindOfFlavor.getValue();   //记录当前规格虚拟机数量
            count+=aKindOfFlavor.getValue();     //虚拟机总数累加
        }

        //一直循环直到count=0，即虚拟机全部放置完毕
        while(count!=0){
            Map<Integer, Integer> aServer=new HashMap<Integer, Integer>();
            currentCPU=physicsInfo[0];
            currentMEM=physicsInfo[1]*1024;
            for(int i=14;i>=0;i--){    //先放排序后在后面规格的虚拟机，因为后面规格的虚拟机的CPU和内存较大
                if(currentFlavorCount[flavorID[i]-1]!=0 && currentCPU>=flavorsInfo[flavorID[i]-1][0] && currentMEM>=flavorsInfo[flavorID[i]-1][1]){
                    int n=currentFlavorCount[flavorID[i]-1];
                    for(int j=0;j<n;j++){
                        if(currentCPU>=flavorsInfo[flavorID[i]-1][0] && currentMEM>=flavorsInfo[flavorID[i]-1][1]){  //CPU和内存大小满足，可以放置
                            //减去相应的容量
                            currentCPU-=flavorsInfo[flavorID[i]-1][0];
                            currentMEM-=flavorsInfo[flavorID[i]-1][1];
                            currentFlavorCount[flavorID[i]-1]--;
                            count--;

                            //先判断该服务器上是否已有此种型号的虚拟机，有则+1，没有则置1
                            if(aServer.containsKey(flavorID[i])){
                                aServer.put(flavorID[i],aServer.get(flavorID[i])+1);
                            }
                            else {
                                aServer.put(flavorID[i],1);
                            }
                        }
                    }
                }
            }
            //运行到这里说明目前服务器的容量已经不足或者虚拟机已经全部放置完毕
            serverList.add(aServer);
        }
        return serverList;
    }





}
