package com.co2team.covidbuster.ui.history

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.co2team.covidbuster.R
import com.co2team.covidbuster.model.RoomCo2Data
import com.co2team.covidbuster.service.BackendService
import com.co2team.covidbuster.service.OnDataReceivedCallback
import com.co2team.covidbuster.ui.roomlist.EXTRA_ROOM_ID
import com.co2team.covidbuster.util.Constants
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.*
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener

class HistoryFragment : Fragment(), OnChartValueSelectedListener {

    companion object {
        fun newInstance(roomId: Int): HistoryFragment{
            val fragment = HistoryFragment()
            val args = Bundle()
            args.putInt(EXTRA_ROOM_ID, roomId)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var co2LineChart: LineChart

    private lateinit var lastTimeUpdatedTime: TextView
    private lateinit var lastTimeUpdatedDate: TextView
    private lateinit var lastTimeUpdatedCO2Value: TextView
    private lateinit var lastTimeUpdatedSafetyStatus: TextView

    // Constants for limit lines
    private val limitLineDangerThreshold = Constants.DANGEROUS_CO2_THRESHOLD
    private val limitLineWarningThreshold = Constants.WARNING_CO2_THRESHOLD

    // "T" is used to split date from time inside a String -> 2007-12-03T10:15:30
    private val localDateTimeDelimiter = "T"

    private val backendService = BackendService()
    private val roomCo2DataList = ArrayList<RoomCo2Data>() // capturing the LiveData RoomDataList inside a local variable
    private val roomCo2DataCreatedDataList = ArrayList<String>() // x-Axis values of the Line Chart -> necessary in the IndexValueFormatter
    private var roomIdExtra: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.history_fragment, container, false)

        val args = arguments
        roomIdExtra = args!!.getInt(EXTRA_ROOM_ID)

        co2LineChart = view.findViewById(R.id.co2LineChart)
        lastTimeUpdatedTime = view.findViewById(R.id.lastTimeUpdatedTimeTv)
        lastTimeUpdatedDate = view.findViewById(R.id.lastTimeUpdatedDateTv)
        lastTimeUpdatedCO2Value = view.findViewById(R.id.lastTimeUpdatedCo2ValueTv)
        lastTimeUpdatedSafetyStatus = view.findViewById(R.id.lastTimeUpdatedSafetyStatusTv)

        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        backendService.readCo2MeasurementsForRoom(roomIdExtra, object : OnDataReceivedCallback {
            override fun onSuccess(roomCo2Data: List<RoomCo2Data>) {

                val data = createOrLoadLineData()
                val set = createOrLoadLineDataSet(data)

                if(roomCo2Data.isNotEmpty()) {
                    this@HistoryFragment.roomCo2DataList.addAll(roomCo2Data)

                    for (roomData in roomCo2Data) {
                        val yAxisRepresentingCo2Ppm = roomData.co2ppm.toFloat()

                        val roomDataCreatedDate: String = roomData.created.toString().substringAfter(localDateTimeDelimiter)
                        roomCo2DataCreatedDataList.add(roomDataCreatedDate)

                        val entry = Entry(set.entryCount.toFloat(), yAxisRepresentingCo2Ppm)
                        co2LineChart.data.addEntry(entry, 0)
                    }

                    co2LineChart.xAxis.valueFormatter = IndexAxisValueFormatter(roomCo2DataCreatedDataList)
                    co2LineChart.data.notifyDataChanged()
                    co2LineChart.notifyDataSetChanged()
                    co2LineChart.moveViewToX(co2LineChart.data.entryCount - 7.toFloat())

                    // Update Line Graph
                    updateUIElementsAccordingToRoomData(roomCo2Data.last(), set)
                } else {
                    lastTimeUpdatedDate.text = "-"
                    lastTimeUpdatedTime.text = "-"
                    lastTimeUpdatedCO2Value.text = "-"
                    lastTimeUpdatedSafetyStatus.text = getString(R.string.history_fragment_status_unknown)
                }
            }
        })

        setupLineChart()
        setupAxis()
        setupLimitLines()
        setupLegend()
    }

    private fun createOrLoadLineData(): LineData {
        var data = co2LineChart.data

        // if LineData object has no data, create a new empty one and set it to the line graph
        if (data == null) {
            data = LineData()
            co2LineChart.data = data
        }
        return data
    }

    private fun createOrLoadLineDataSet(data: LineData): LineDataSet {
        var set = data.getDataSetByIndex(0) as LineDataSet? // take the data set from the LineData object

        // if LineData has no DataSet yet, create a new empty one and add it to the LineData object
        if (set == null) {
            set = LineDataSet(null, null)
            setupLineDataSet(set)
            data.addDataSet(set)
        }
        return set
    }

    private fun setupLineChart() {
        co2LineChart.description.isEnabled = false
        co2LineChart.setNoDataText(getString(R.string.history_fragment_no_data_text))
        co2LineChart.setBackgroundColor(ContextCompat.getColor(requireActivity().applicationContext, R.color.colorPrimary))

        co2LineChart.setTouchEnabled(true)  // enable touch gestures

        // disabling scaling (default is enable)
        // if using scaling is recommended to use also "co2LineChart.setPinchZoom(true);" for avoiding scaling x and y separately
        co2LineChart.setScaleEnabled(false)
        co2LineChart.isDragEnabled = true

        co2LineChart.setOnChartValueSelectedListener(this)

        // enable value highlighting
        co2LineChart.isHighlightPerTapEnabled = true
        co2LineChart.isHighlightPerDragEnabled = true

        co2LineChart.setExtraOffsets(0f, 0f, 0f, 16f)   // spacing between x axis and legend

        co2LineChart.animateX(2000)
        co2LineChart.invalidate()   // refresh the drawing
    }

    private fun setupLineDataSet(lineDataSet: LineDataSet) {
        lineDataSet.lineWidth = 2.5f
        lineDataSet.color = ContextCompat.getColor(requireActivity().applicationContext, R.color.colorAccent)

        lineDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        lineDataSet.cubicIntensity = 0.2f
        lineDataSet.axisDependency = YAxis.AxisDependency.LEFT  // show only the left y axis

        lineDataSet.setDrawCircles(true)
        lineDataSet.circleRadius = 4f
        lineDataSet.setCircleColor((ContextCompat.getColor(requireActivity().applicationContext, R.color.colorPrimary)))
        lineDataSet.setDrawCircleHole(true)
        lineDataSet.circleHoleColor = (ContextCompat.getColor(requireActivity().applicationContext, R.color.white))
        lineDataSet.circleHoleRadius = 2.5f

        lineDataSet.highlightLineWidth = 1f
        lineDataSet.highLightColor = ContextCompat.getColor(requireActivity().applicationContext, R.color.transparent_75)

        lineDataSet.setDrawValues(false)

        // fill line underneath
        lineDataSet.setDrawFilled(true)
        lineDataSet.fillFormatter = IFillFormatter { _, _ -> co2LineChart.axisLeft.axisMinimum }
        val drawable = ContextCompat.getDrawable(requireActivity().applicationContext, R.drawable.fade_accent_safe)
        lineDataSet.fillDrawable = drawable
    }

    private fun setupAxis() {
        val rightAxis = co2LineChart.axisRight
        rightAxis.isEnabled = false // disable right axis
        val leftAxis = co2LineChart.axisLeft
        leftAxis.textColor = Color.WHITE
        leftAxis.textSize = 10f
        leftAxis.setDrawAxisLine(false) // remove y axis line

        leftAxis.xOffset = 12f  // space between y axis and y axis value labels

        leftAxis.enableGridDashedLine(10f, 10f, 0f)
        leftAxis.setDrawLimitLinesBehindData(true)

        val bottomAxis = co2LineChart.xAxis
        bottomAxis.textColor = Color.WHITE
        bottomAxis.textSize = 10f
        bottomAxis.setDrawAxisLine(false)

        // starts immediately with six empty x values
        bottomAxis.spaceMin = 0f
        bottomAxis.spaceMax = 6f
        bottomAxis.setDrawGridLines(false)
        bottomAxis.enableGridDashedLine(10f, 10f, 0f)
        bottomAxis.setDrawLimitLinesBehindData(true)
        bottomAxis.position = XAxis.XAxisPosition.BOTTOM
    }

    private fun setupLimitLines() {
        co2LineChart.axisLeft.removeAllLimitLines()
        val dangerLimit = LimitLine(limitLineDangerThreshold.toFloat())
        dangerLimit.lineWidth = 1f
        dangerLimit.lineColor = ContextCompat.getColor(requireActivity().applicationContext, R.color.covidbuster_danger_zone_red)

        val warningLimit = LimitLine(limitLineWarningThreshold.toFloat())
        warningLimit.lineWidth = 1f
        warningLimit.lineColor = ContextCompat.getColor(requireActivity().applicationContext, R.color.covidbuster_warning_zone_yellow)

        co2LineChart.axisLeft.addLimitLine(dangerLimit)
        co2LineChart.axisLeft.addLimitLine(warningLimit)
    }

    private fun setupLegend() {
        // get the legend (only possible after setting data)
        val l = co2LineChart.legend
        l.textColor = Color.WHITE
        l.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
        l.isWordWrapEnabled = true
        l.xEntrySpace = 16f // spacing between entries
        l.yOffset = 12f     // spacing under legend

        // setup custom legend entries
        val dangerZoneLegend = LegendEntry(getString(R.string.history_fragment_danger_label), Legend.LegendForm.CIRCLE, 10f, 2f, null, ContextCompat.getColor(requireActivity().applicationContext, R.color.covidbuster_danger_zone_red))
        val warningZoneLegendEntry = LegendEntry(getString(R.string.history_fragment_warning_label), Legend.LegendForm.CIRCLE, 10f, 2f, null, ContextCompat.getColor(requireActivity().applicationContext, R.color.covidbuster_warning_zone_yellow))
        val safeZoneLegendEntry = LegendEntry(getString(R.string.history_fragment_safe_label), Legend.LegendForm.CIRCLE, 10f, 2f, null, ContextCompat.getColor(requireActivity().applicationContext, R.color.covidbuster_safe_zone_green))
        l.setCustom(arrayOf(dangerZoneLegend, warningZoneLegendEntry, safeZoneLegendEntry))
    }

    private fun updateUIElementsAccordingToRoomData(roomCo2Data: RoomCo2Data, set: LineDataSet?) {
        Handler(Looper.getMainLooper()).post {

            val drawable: Drawable
            val colorId: Int
            val safetyStatus: String

            when {
                roomCo2Data.co2ppm > limitLineDangerThreshold -> {
                    drawable = ContextCompat.getDrawable(requireActivity().applicationContext, R.drawable.fade_accent_danger)!!
                    colorId = ContextCompat.getColor(requireActivity().applicationContext, R.color.covidbuster_danger_zone_red)
                    safetyStatus = getString(R.string.history_fragment_danger_label)
                }
                roomCo2Data.co2ppm > limitLineWarningThreshold -> {
                    drawable = ContextCompat.getDrawable(requireActivity().applicationContext, R.drawable.fade_accent_warning)!!
                    colorId = ContextCompat.getColor(requireActivity().applicationContext, R.color.covidbuster_warning_zone_yellow)
                    safetyStatus = getString(R.string.history_fragment_warning_label)
                }
                else -> {
                    drawable = ContextCompat.getDrawable(requireActivity().applicationContext, R.drawable.fade_accent_safe)!!
                    colorId = ContextCompat.getColor(requireActivity().applicationContext, R.color.covidbuster_safe_zone_green)
                    safetyStatus = getString(R.string.history_fragment_safe_label)
                }
            }

            set?.fillDrawable = drawable
            set?.color = colorId

            val localDateSubstring = roomCo2Data.created.toString().substringBefore(localDateTimeDelimiter)
            val localTimeSubstring = roomCo2Data.created.toString().substringAfter(localDateTimeDelimiter)
            lastTimeUpdatedDate.text = localDateSubstring
            lastTimeUpdatedTime.text = localTimeSubstring
            lastTimeUpdatedCO2Value.text =  getString(R.string.history_fragment_ppm, roomCo2Data.co2ppm.toString())
            lastTimeUpdatedSafetyStatus.text = safetyStatus
            lastTimeUpdatedSafetyStatus.setTextColor(colorId)
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight?) {
        updateUIElementsAccordingToRoomData(roomCo2DataList[e!!.x.toInt()], co2LineChart.data.getDataSetByIndex(0) as LineDataSet?)
    }

    override fun onNothingSelected() {
        // no need to do anything here
    }
}