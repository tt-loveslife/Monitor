package com.zhanbp.bloodpressuremonitor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.blakequ.bluetooth_manager_lib.BleManager;
import com.blakequ.bluetooth_manager_lib.BleParamsOptions;
import com.blakequ.bluetooth_manager_lib.connect.BluetoothSubScribeData;
import com.blakequ.bluetooth_manager_lib.connect.ConnectConfig;
import com.blakequ.bluetooth_manager_lib.connect.ConnectState;
import com.blakequ.bluetooth_manager_lib.connect.ConnectStateListener;
import com.blakequ.bluetooth_manager_lib.connect.multiple.MultiConnectManager;
import com.blakequ.bluetooth_manager_lib.device.resolvers.GattAttributeResolver;
import com.leon.lfilepickerlibrary.LFilePicker;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import de.greenrobot.event.EventBus;



public class ChartsActivity extends Activity implements View.OnClickListener {    //再加个文本框平均压显示

	private static final String TAG = "message";



	/*组件相关*/
	private LinearLayout layout_chart1;
	private Button startbutton1;
	private Button stopbutton;
	private Button savebutton;
	private Button liftUp;
	private Button saveCons;
	private Button LowP;
	private Button startMears;


	private TextView BLE;
	private TextView BLE_offset;
	private TextView Condition;
	private TextView PSshow;
	private TextView PDshow;
	private TextView PMshow;

	private EditText PS;
	private EditText PD;

	private Timer timer = new Timer();    //定时器
	private TimerTask task;

	private ProgressDialog progressDialog;

	/*图表相关*/
	private Handler handler;            //渲染器
	private GraphicalView chart1;
	private XYMultipleSeriesRenderer renderer1;
	private XYMultipleSeriesDataset dataset1;        //一个图的数据组
	private TimeSeries series11;                        //一组数据
	private TimeSeries series12;

	private TimeSeries seriesDC1;//
	private TimeSeries seriesDC2;

	/*数据相关*/
	boolean CONNECT_STATA;//连接状态
	boolean RECORD_STATA;//数据保存状态
	boolean mearsure_flag = false;//开始测量标志位
	int updateFlag = 0;//测量周期20*50ms

	int[] BP_DATA_BUFFER = new int[10]; //接收数据buffer，更新图表用
	int[] ycache = new int[201];        //Y缓存，更新用
	private ArrayList<Integer>[] BP_Record_Data = new ArrayList[10];//数据集,保存记录用(0)S1 (1)S2 (3)S1DC (4)S2DC

	private File file;
	private String ExcelName;//保存excel文件名
	private String[] title = {"Signal-1", "Signal-2", "DCSignal-1", "DCSignal-2"};
	String FilePath;

	//int[][] pressure_data=new int[2][9100];//faker
	//int pressure_index = 0;

	/*信号处理相关*/
	Signal signal10ms = new Signal();
	boolean test_flag = true;//前状态测试标志
	boolean startrecord_flag = false;
	boolean lowPtime_flag = false;//开始20s计时状态
	int lowPtime = 0;//保证20s低加压状态


	int[] maxAC = new int[3];//max, index1, index2 （AC最大振幅）
	int PbpMean;//AC振幅最大 对应的直流DC
	float PMac1;//低加压状态下AC pp值
	float Pmear_dc0;//低加压（参考）DC
	float Pmear_dc1;//提升时DC
	final float deltaP = (float) 5.88; //mmHG (10mm水柱)
	final float Sensitivity = (float) 8.53; //mv/mmHG
	float Poffset;//和标准血压的偏置
	float Kbp;//血压指数，通过AC最大时计算
	float KmodiDC;//系数
	float KmodiAC;
	int PSvalue, PDvalue; //输入数据
	float PMvalue;

	/*蓝牙相关*/
	MultiConnectManager multiConnectManager;  //多设备连接
	private BluetoothAdapter bluetoothAdapter;   //蓝牙适配器
	private BluetoothDevice bluetoothDevice;

	private ArrayList<String> connectDeviceMacList; //需要连接的mac设备集合
	private ArrayList<String> connectDeviceNameList; //需要连接的mac设备集合
	ArrayList<String> filePaths = new ArrayList<>(); // 文件路径
	ArrayList<BluetoothGatt> gattArrayList; //设备gatt集合

	private final int REQUEST_CODE_PERMISSION = 1; // 权限请求码  用于回调

	public ChartsActivity() {
	}

	private class DATALOADER extends AsyncTask<Void, Void, int[][]> {
		protected void onPreExecute() {
			ChartsActivity.this.progressDialog.setMessage("加载中,请稍后......");
			ChartsActivity.this.progressDialog.setCanceledOnTouchOutside(false);
			ChartsActivity.this.progressDialog.show();
			Log.i("here", "total cols is ");
		}

		protected int[][] doInBackground(Void[] paramArrayOfVoid) {
			ExcelIO EIO = new ExcelIO(ChartsActivity.this);
			return EIO.getXlsData("Pressure3.xls", 0);
		}

		protected void onPostExecute(int[][] paramList) {
			if ((ChartsActivity.this.progressDialog != null) && (ChartsActivity.this.progressDialog.isShowing()))
				ChartsActivity.this.progressDialog.dismiss();
			if (paramList != null) {
				//pressure_data = paramList;
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_chart);


		initVariables();
		Intent ConnectDevices = getIntent();
		connectDeviceMacList = ConnectDevices.getStringArrayListExtra("connectMAC");
		connectDeviceNameList = ConnectDevices.getStringArrayListExtra("connectNAME");

		initView();
		initEvent();
		initRenderandDataset();
		initChart();

		requestWritePermission();
		initBLEConfig();  // 蓝牙初始设置

		EventBus.getDefault().register(this);//   ②注册事件


		ProgressDialog localProgressDialog = new ProgressDialog(this);
		this.progressDialog = localProgressDialog;
		new DATALOADER().execute();

		handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				// 确认连接成功后，刷新数据
				if (CONNECT_STATA) {
					for (int i = 0; i < 5; i++) {
						updateChart(chart1, dataset1, series11, series12, BP_DATA_BUFFER[2 * i], BP_DATA_BUFFER[2 * i + 1]);
					}
					updateSeriesDC(BP_DATA_BUFFER[2], BP_DATA_BUFFER[3]);
				}
				BLE_offset.setText(Html.fromHtml("<font color = \"#0000FF\">S1_DC:" + String.format("%04d", BP_DATA_BUFFER[2]) + "</font>" + " "));
				//+ "<font color='#FF0000'>S2_DC:"+String.format("%04d",BP_DATA_BUFFER[3])+"</font>"+" "));
				//if(CONNECT_STATA && mearsure_flag && updateFlag==200){//200*50=10s
				if (CONNECT_STATA && mearsure_flag && updateFlag == 100) {//100*100=10s
					updateFlag = 0;
					updateBPTextView(series11, series12, seriesDC1, seriesDC2);//用series11即可
				}
				if (lowPtime_flag == true && lowPtime < 220) {
					lowPtime++;
				}
				updateFlag++;
				super.handleMessage(msg);
			}
		};

		task = new TimerTask() {
			@Override
			public void run() {
				Message message = new Message();
				message.what = 200;//用户自定义码,ID
				handler.sendMessage(message);
			}
		};

		timer.schedule(task, 1, 100);//p1：要操作的方法，p2：要设定延迟的时间，p3：周期的设定（ms单位）

		connentBluetooth();
	}

	private void initVariables() {
		connectDeviceMacList = new ArrayList<>();
		gattArrayList = new ArrayList<>();

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();//bluetoothAdapter的初始化

		CONNECT_STATA = false;
		RECORD_STATA = false;
		mearsure_flag = false;

		for (int j = 0; j < 10; j++) {
			BP_DATA_BUFFER[j] = 0;
		}

		for (int j = 0; j < 10; j++) {
			BP_Record_Data[j] = new ArrayList<Integer>();
		}
		seriesDC1 = new TimeSeries("BP-DC-1");
		seriesDC2 = new TimeSeries("BP-DC-2");
		int nr = 201;                                        //200个数
		for (int k = 0; k < nr; k++) {
			seriesDC1.add(k, 0);    //初始化为0
			seriesDC2.add(k, 0);
		}
	}

	private void initView() {
		// 初始化图表
		layout_chart1 = (LinearLayout) findViewById(R.id.linearlayout_chart1);

		// 初始化按钮
		saveCons = (Button) this.findViewById(R.id.saveConstButton);
		savebutton = (Button) this.findViewById(R.id.saveButton1);
		startbutton1 = (Button) this.findViewById(R.id.startButton1);
		stopbutton = (Button) this.findViewById(R.id.stopButton1);
		startMears = (Button) this.findViewById(R.id.startMears);
		LowP = (Button) this.findViewById(R.id.LowP);
		liftUp = (Button) this.findViewById(R.id.LiftUp);

		// 初始化蓝牙配件
		BLE = (TextView) this.findViewById(R.id.ble1);
		BLE_offset = (TextView) this.findViewById(R.id.ble1_offset);
		Condition = (TextView) this.findViewById(R.id.condition);
		PSshow = (TextView) this.findViewById(R.id.PS);
		PDshow = (TextView) this.findViewById(R.id.PD);
		PMshow = (TextView) this.findViewById(R.id.PM);
		for (int i = 0; i < connectDeviceNameList.size(); i++) {
			BLE.setText(connectDeviceNameList.get(i));
		}

		PS = (EditText) this.findViewById(R.id.editText1);
		PD = (EditText) this.findViewById(R.id.editText2);
	}

	private void initEvent() {
		// 唤醒按钮事件处理
		startbutton1.setOnClickListener(this);
		stopbutton.setOnClickListener(this);
		savebutton.setOnClickListener(this);
		liftUp.setOnClickListener(this);
	}

	private void initRenderandDataset() {
		renderer1 = getDemoRenderer(new XYMultipleSeriesRenderer(2));
		series11 = new TimeSeries("BP");    //
		series12 = new TimeSeries("ECG");
		dataset1 = getDateDemoDataset(new XYMultipleSeriesDataset(), series11, series12);
	}

	private void initChart() {
		chart1 = ChartFactory.getLineChartView(this, dataset1, renderer1);
		layout_chart1.addView(chart1, new LayoutParams(LayoutParams.WRAP_CONTENT, 600));
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			/*开始记录*/
			case R.id.startButton1:
				//将数据清零，重新开始记录
				for (int i = 0; i < 10; i++) {
					BP_Record_Data[i].clear();
				}
				RECORD_STATA = true;//记录数据标志位
				savebutton.setText(this.getString(R.string.SaveData));//重置保存按钮
				lowPtime_flag = false;//重置低加压状态
				lowPtime = 0;
				break;

			/*停止记录*/
			case R.id.stopButton1:
				if (startrecord_flag && !BP_Record_Data[0].isEmpty()) {
					RECORD_STATA = false;
					savebutton.setText(this.getString(R.string.preSaveData));//提示保存数据
				}
				break;

			/*保存数据*/
			case R.id.saveButton1:
				if (!BP_Record_Data[0].isEmpty()) {
					setExcelNameAndSave(0);//弹窗取名//保存
				} else {
					Toast.makeText(this, "未记录任何数据", Toast.LENGTH_LONG).show();
				}
				break;

			/*发送文件*/
            case R.id.LiftUp:
				int REQUESTCODE_FROM_ACTIVITY = 1000;
				new LFilePicker()
						.withActivity(this)
						.withRequestCode(REQUESTCODE_FROM_ACTIVITY)
						.withStartPath("/storage/emulated/0")//指定初始显示路径
						.withIsGreater(false)//过滤文件大小 小于指定大小的文件
						.withFileSize(5 * 1024 * 1024)//指定文件大小为 5M
						.start();
                break;
		}
	}

	//弹窗取名
	private void setExcelNameAndSave(final int index) {
		final EditText setExcelName = new EditText(this);
		new AlertDialog.Builder(this)
				.setTitle("请输入文件名")
				.setView(setExcelName)
				.setPositiveButton("确定", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						//按下确定键后的事件
						Toast.makeText(getApplicationContext(), setExcelName.getText().toString(), Toast.LENGTH_LONG).show();
						ExcelName = "Sensor_BP" + (index + 1) + "_" + setExcelName.getText().toString();
						Log.i("chartActivitySave", ExcelName);
						saveData(index);
					}
				})
				.setNegativeButton("取消", null).show();
	}

	//保存数据
	private void saveData(int Sensor_index) {
		//String path = this.getFilesDir().getAbsolutePath();
		File externalStoreage = Environment.getExternalStorageDirectory();
		String externalStoragePath = externalStoreage.getAbsolutePath();//获取存储位置
		String directory = externalStoragePath + File.separator + "BloodPressure";//创建路径
		File filepath = new File(directory);
		if (!filepath.exists()) {
			filepath.mkdirs();
		}
		String fileName = ExcelName + ".xls";
		File xlsFile = new File(filepath, fileName);//然后再创建文件的File对象    //文件和文件夹必须分开创建！
		String absolutePath = xlsFile.getAbsolutePath();
		if (absolutePath != null) {
			Log.i("chartActivitySave", "save path exist");
			ExcelIO.initExcel(xlsFile, absolutePath, title);//
			ExcelIO.writeObjListToExcel(BP_Record_Data, absolutePath, this);
			savebutton.setText(this.getString(R.string.SaveData));//重置保存按钮
		}
	}

	//更新图表数据
	private void updateChart(GraphicalView chart, XYMultipleSeriesDataset Dataset, TimeSeries series1, TimeSeries series2, int addYY1, int addYY2) {
		boolean s1 = false;
		int length = series1.getItemCount();
		int length2 = series2.getItemCount();

		if (length >= 201) length = 201;                //50ms一个数据10s的数据量
		if (length2 >= 201) length2 = 201;

		for (int i = 0; i < length; i++) {                //数据存入缓存
			ycache[i] = (int) series1.getY(i);
		}
		series1.clear();                                //清除数据
		for (int k = 0; k < length - 1; k++) {                //加入缓存中的数据
			series1.add(k, ycache[k + 1]);
		}
		series1.add(length - 1, addYY1);                //加入新数据
		///////////////////////////////////
		for (int i = 0; i < length2; i++) {                //数据存入缓存
			ycache[i] = (int) series2.getY(i);
		}
		series2.clear();                                //清除数据
		for (int k = 0; k < length2 - 1; k++) {                //加入缓存中的数据
			series2.add(k, ycache[k + 1]);
		}
		series2.add(length2 - 1, addYY2);                //加入新数据
		if (test_flag && s1 && signal10ms.meanPPvalue(signal10ms.findPeak(ycache)) >= 1500)//计算另一路信号平均峰峰值是否大于1500
		{

		}
		Dataset.removeSeries(series1);                //更新数据组
		Dataset.removeSeries(series2);
		Dataset.addSeries(series1);
		Dataset.addSeries(series2);
		chart.invalidate();
	}

	private void updateSeriesDC(int addDC1, int addDC2) {
		int length = seriesDC1.getItemCount();
		int length2 = seriesDC2.getItemCount();

		if (length >= 201) length = 201;                //50ms一个数据10s的数据量
		if (length2 >= 201) length2 = 201;

		for (int i = 0; i < length; i++) {                //数据存入缓存
			ycache[i] = (int) seriesDC1.getY(i);
		}
		seriesDC1.clear();                                //清除数据
		for (int k = 0; k < length - 1; k++) {                //加入缓存中的数据
			seriesDC1.add(k, ycache[k + 1]);
		}
		seriesDC1.add(length - 1, addDC1);                //加入新数据
		///////////////////////////////////
		for (int i = 0; i < length2; i++) {                //数据存入缓存
			ycache[i] = (int) seriesDC2.getY(i);
		}
		seriesDC2.clear();                                //清除数据
		for (int k = 0; k < length2 - 1; k++) {                //加入缓存中的数据
			seriesDC2.add(k, ycache[k + 1]);
		}
		seriesDC2.add(length2 - 1, addDC2);                //加入新数据
	}

	//更新血压数据显示
	private void updateBPTextView(TimeSeries ac1, TimeSeries ac2, TimeSeries dc1, TimeSeries dc2) {//更新血压
		int sum = 0;
		float PS = 0;
		float PD = 0;
		float P = 0;
		float PPvalue = 0;
		ArrayList[] peakpeak;
		int length;
		length = dc1.getItemCount();
		for (int i = 0; i < length; i++) {                //dc平均值
			sum += (int) dc1.getY(i);
		}
		P = PMvalue + KmodiDC * (sum / length - Pmear_dc0);

		length = ac1.getItemCount();
		for (int i = 0; i < length; i++) {                //数据存入缓存
			ycache[i] = (int) ac1.getY(i);
		}
	}

	private XYMultipleSeriesRenderer getDemoRenderer(XYMultipleSeriesRenderer renderer) {
		renderer.setChartTitle("BP&ECG");//标题
		renderer.setChartTitleTextSize(45);
		renderer.setXTitle("Time");    //X标签
		renderer.setAxisTitleTextSize(20);//轴标签大小
		renderer.setAxesColor(Color.WHITE);//轴颜色
		renderer.setLabelsTextSize(20);    //轴刻度大小
		renderer.setLabelsColor(Color.BLACK);//轴刻度颜色
		renderer.setShowLegend(true);        //图例
		renderer.setLegendTextSize(20);    //
		renderer.setXLabelsColor(Color.WHITE);
		renderer.setYLabelsColor(0, Color.BLACK);
		renderer.setGridColor(Color.LTGRAY);//网格颜色
		renderer.setApplyBackgroundColor(true);
		renderer.setBackgroundColor(Color.WHITE);//背景颜色
		renderer.setMargins(new int[]{70, 20, 30, 5});//边距 上 左 下 右
		XYSeriesRenderer r1 = new XYSeriesRenderer();
		r1.setColor(Color.BLUE);//数据点颜色
		r1.setChartValuesTextSize(15);
		r1.setChartValuesSpacing(1);
		r1.setPointStyle(PointStyle.POINT);//数据点形状
		r1.setFillBelowLine(false);
		r1.setFillPoints(true);
		XYSeriesRenderer r2 = new XYSeriesRenderer();
		r2.setColor(Color.RED);//数据点颜色
		r2.setChartValuesTextSize(15);
		r2.setChartValuesSpacing(1);
		r2.setPointStyle(PointStyle.POINT);//数据点形状
		r2.setFillBelowLine(false);
		r2.setFillPoints(true);
		renderer.addSeriesRenderer(r1);
		renderer.addSeriesRenderer(r2);
		renderer.setMarginsColor(Color.WHITE);
		renderer.setPanEnabled(true, true);
		renderer.setShowGrid(true);
		renderer.setYAxisMax(4000);//Y最大范围
		renderer.setYAxisMin(-2000);//Y最小范围
		renderer.setInScroll(true);  //滚动
		return renderer;
	}

	private XYMultipleSeriesDataset getDateDemoDataset(XYMultipleSeriesDataset dataset, TimeSeries series1, TimeSeries series2) {
		final int nr = 201;                                        //200个数
		for (int k = 0; k < nr; k++) {
			series1.add(k, 0);    //初始化为0
			series2.add(k, 0);
		}
		dataset.addSeries(series1);                            //图表数据集加入数据组
		dataset.addSeries(series2);

		Log.i(TAG, dataset.toString());
		return dataset;                                    //返回数据集
	}

	/*对蓝牙的初始化操作*/
	private void initBLEConfig() {
		for (int i = 0; i < connectDeviceMacList.size(); i++) {
			BluetoothGatt gatt = bluetoothAdapter.getRemoteDevice(connectDeviceMacList.get(i)).connectGatt(this, false, new BluetoothGattCallback() {
			});
			gattArrayList.add(gatt);
			Log.i("zbp", "添加" + connectDeviceMacList.get(i));
		}
		// 获取多蓝牙适配器
		multiConnectManager = BleManager.getMultiConnectManager(this);
		try {
			// 获取蓝牙适配器
			bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (bluetoothAdapter == null) {
				Toast.makeText(this, "蓝牙不可用", Toast.LENGTH_LONG).show();
				return;
			}
			// 蓝牙没打开的时候打开蓝牙
			if (!bluetoothAdapter.isEnabled())
				bluetoothAdapter.enable();
		} catch (Exception err) {
			Log.i("zbp", "something wrong");
		}

		BleManager.setBleParamsOptions(new BleParamsOptions.Builder()
				.setBackgroundBetweenScanPeriod(1 * 60 * 1000)//在后台时（不可见扫描界面）扫描间隔暂停时间，我们扫描的方式是间隔扫描
				.setBackgroundScanPeriod(10000)//在后台时（不可见扫描界面）扫描持续时间
				.setForegroundBetweenScanPeriod(2000)//在前台时（可见扫描界面）扫描间隔暂停时间，我们扫描的方式是间隔扫描
				.setForegroundScanPeriod(10000)//在前台时（可见扫描界面）扫描持续时间
				.setDebugMode(BuildConfig.DEBUG)
				.setMaxConnectDeviceNum(4)            //最大可以连接的蓝牙设备个数
				.setReconnectBaseSpaceTime(1000)      //重连基础时间间隔 ms，重连的时间间隔
				.setReconnectMaxTimes(Integer.MAX_VALUE)//最大重连次数，默认可一直进行重连
				.setReconnectStrategy(ConnectConfig.RECONNECT_LINE_EXPONENT)
				.setReconnectedLineToExponentTimes(5)//快速重连的次数(线性到指数，只在 reconnectStrategy=ConnectConfig.RECONNECT_LINE_EXPONENT 时有效)
				.setConnectTimeOutTimes(5000)////连接超时时间 15s,15s 后自动检测蓝牙状态
				// （如果设备不在连接范围或蓝牙关闭，则重新连接的时间会很长，或者一直处于连接的状态，现在超时后会自动检测当前状态
				.build());
	}

	/*连接需要连接的传感器*/
	private void connentBluetooth() {
		String[] objects = connectDeviceMacList.toArray(new String[connectDeviceMacList.size()]);
		multiConnectManager.addDeviceToQueue(objects);
		multiConnectManager.addConnectStateListener(new ConnectStateListener() {
			@Override
			public void onConnectStateChanged(String address, ConnectState state) {
				switch (state) {
					case CONNECTING:
						Log.i("connectStateX", "设备:" + address + "连接状态:" + "正在连接");
						break;
					case CONNECTED:
						String[] devices = connectDeviceMacList.toArray(new String[connectDeviceMacList.size()]);
						for (int i = 0; i < connectDeviceMacList.size(); i++) {
							if (address.equals(devices[i]))
								CONNECT_STATA = true;
						}
						Log.i("connectStateX", "设备:" + address + "连接状态:" + "成功");
						break;
					case NORMAL:
						String[] devices2 = connectDeviceMacList.toArray(new String[connectDeviceMacList.size()]);
						for (int i = 0; i < connectDeviceMacList.size(); i++) {
							if (address.equals(devices2[i]))
								CONNECT_STATA = false;
						}
						Log.i("connectStateX", "设备:" + address + "连接状态:" + "失败");
						break;
				}
			}
		});

		/*数据回调*/
		multiConnectManager.setBluetoothGattCallback(new BluetoothGattCallback() {
			@Override
			public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
				super.onCharacteristicChanged(gatt, characteristic);
				dealCallDatas(gatt, characteristic);
			}
		});
		//1.服务UUID
		//multiConnectManager.setServiceUUID("0000c3ab-0000-1000-8000-00805f9b34fb");
		multiConnectManager.setServiceUUID("0003A150-0000-1000-8000-00805F9B0131");
		//2.clean history descriptor data（清除历史订阅读写通知）
		multiConnectManager.cleanSubscribeData();
		//3.add subscribe params（读写和通知）
		multiConnectManager.addBluetoothSubscribeData(
				new BluetoothSubScribeData.Builder().
						setDescriptorWrite(UUID.fromString("0003A151-0000-1000-8000-00805F9B0131"), UUID.fromString(GattAttributeResolver.CLIENT_CHARACTERISTIC_CONFIG), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE).build());//特性UUID启用CCCD
		multiConnectManager.addBluetoothSubscribeData(
				new BluetoothSubScribeData.Builder().
						setDescriptorWrite(UUID.fromString("0003A151-0000-1000-8000-00805F9B0131"), UUID.fromString(GattAttributeResolver.CLIENT_CHARACTERISTIC_CONFIG), BluetoothGattDescriptor.ENABLE_INDICATION_VALUE).build());
		//还有读写descriptor
		multiConnectManager.addBluetoothSubscribeData(
				//new BluetoothSubScribeData.Builder().setCharacteristicNotify(UUID.fromString("0000e1d3-0000-1000-8000-00805f9b34fb")).build());//特性UUID
				new BluetoothSubScribeData.Builder().setCharacteristicNotify(UUID.fromString("0003A151-0000-1000-8000-00805F9B0131")).build());
		//start descriptor(注意，在使用时当回调onServicesDiscovered成功时会自动调用该方法，所以只需要在连接之前完成1,3步即可)
		for (int i = 0; i < gattArrayList.size(); i++) {
			multiConnectManager.startSubscribe(gattArrayList.get(i));
		}
		multiConnectManager.startConnect();
	}

	/**
	 * 处理回调的数据
	 *
	 * @param gatt
	 * @param characteristic
	 */
	private void dealCallDatas(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
		//int position = connectDeviceMacList.indexOf(gatt.getDevice().getAddress());
		//当前gatt是在List中的第几个
		byte[] value = characteristic.getValue();
		//缓存区，更新时从BP_DATA_BUFFER里取
		for (int i = 0; i < 10; i++) {
			BP_DATA_BUFFER[i] = ((((short) value[2 * i]) << 8) | ((short) value[2 * i + 1] & 0xff));
		}
		//保存数据
		if (RECORD_STATA) {
			for (int i = 0; i < 10; i++) {
				BP_Record_Data[i].add(BP_DATA_BUFFER[i]);
			}
		}
		EventBus.getDefault().post(new RefreshDatas()); // 发送消息，更新UI 显示数据 ④发送事件
	}

	public void onEventMainThread(RefreshDatas event) { //   ③处理事件

	}

	/**
	 * createAt 2019/8/30
	 * description:  权限申请相关，适配6.0+机型 ，蓝牙，文件，位置 权限
	 */
	private String[] allPermissionList = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
			Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
			Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
	};

	/**
	 * 遍历出需要获取的权限
	 */
	private void requestWritePermission() {
		ArrayList<String> permissionList = new ArrayList<>();
		// 将需要获取的权限加入到集合中  ，根据集合数量判断 需不需要添加
		for (int i = 0; i < allPermissionList.length; i++) {
			if (PackageManager.PERMISSION_DENIED == ContextCompat.checkSelfPermission(this, allPermissionList[i])) {
				permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
				//permissionList.add(allPermissionList[i]);
			}
		}
		String permissionArray[] = new String[permissionList.size()];
		for (int i = 0; i < permissionList.size(); i++) {
			permissionArray[i] = permissionList.get(i);
		}
		if (permissionList.size() > 0)
			ActivityCompat.requestPermissions(this, permissionArray, REQUEST_CODE_PERMISSION);
	}

	/**
	 * 权限申请的回调
	 *
	 * @param requestCode
	 * @param permissions
	 * @param grantResults
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == REQUEST_CODE_PERMISSION) {
			if (permissions[0].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				//用户同意使用write
			} else {
				//用户不同意，自行处理即可
				Toast.makeText(ChartsActivity.this, "您取消了权限申请,可能会影响软件的使用,如有问题请退出重试", Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	public void onDestroy() {
		//Timer
		timer.cancel();

		EventBus.getDefault().unregister(this);//   ⑤		取消事件
		super.onDestroy();
	}

	/* 创建socket客户端发送文件 */
	public void client(String filepaths) throws IOException {
		// 1.0 选择需要传输的文件名和存储位置
		String[] strs = filepaths.split("结束了");
		String filepath;
		int temp = 0;
		for (int i = 0; i < strs.length; i++){
			filepath = strs[i];
			int start = filepath.lastIndexOf("/");
			if (start != -1){
				String filename = filepath.substring(start+1);
				String path = filepath.substring(0,start+1);
				FileInputStream fis = new FileInputStream(filepath);
				// 2.0 创建套接字服务,绑定目标服务器
				String ip = "10.180.96.161";
				int port = 8888;
				Socket socket = new Socket(ip,port);

				//3.0 使用套接字获得网络输出流
				OutputStream os = socket.getOutputStream();
//				os.write((filepath + "上传完毕").getBytes()); //用于检测输出结果

				//4.0 使用文件输入流读取本地文件并传输
				os.write((filename + "上传完毕").getBytes());
				os.flush();
				int len = 0;
				byte[] bytes = new byte[1024];
				while((len = fis.read(bytes)) != -1){
					os.write(bytes,0,len);
					os.flush();
				}
				socket.shutdownOutput();

				//5.0 接受服务器反馈是否发送成功
				InputStream is = socket.getInputStream();
				while((len = is.read(bytes)) != -1){
					temp++;
				}

				//6.0 关闭所有的流节省资源
				fis.close();
				socket.close();
			}
		}
		if (temp == strs.length){
			Looper.prepare();
			Toast.makeText(this, "文件全部上传成功", Toast.LENGTH_LONG).show();
			Looper.loop();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			if (requestCode == Consant.REQUESTCODE_FROM_ACTIVITY) {
				List<String> list = data.getStringArrayListExtra(Constant.RESULT_INFO);
				FilePath = "";
				for (int i = 0; i < list.size() ; i++){
					// 循环处理每一个选中的文件
					FilePath += (list.get(i) + "结束了");
				}
				new Thread(){
					public void run(){
						try {
							client(FilePath);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}.start();

//				Toast.makeText(getApplicationContext(), "选中了" + list.size() + "个文件", Toast.LENGTH_SHORT).show();


			}
		}
	}
}


	