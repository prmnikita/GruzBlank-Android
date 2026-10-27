package ru.gruzblank.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var placesBox: LinearLayout
    private lateinit var totals: TextView
    private val fields = linkedMapOf<String, EditText>()
    private val places = mutableListOf<Place>()
    private var restoring = false
    data class Place(val name: EditText, val weight: EditText, val length: EditText, val width: EditText, val height: EditText, val container: LinearLayout)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,32) }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.fastrance_logo)
            adjustViewBounds = true
            maxHeight = 110
            scaleType = ImageView.ScaleType.FIT_START
            contentDescription = "Фастранс"
            setPadding(0, 0, 0, 12)
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        titleText("ЗАЯВЛЕНИЕ НА ОТПРАВКУ ГРУЗА")
        field("Номер бланка (необязательно)", "number")
        field("Город отправления", "city")
        field("Назначение", "destination")
        titleText("ПОЛУЧАТЕЛЬ")
        field("Получатель", "recipient")
        titleText("ОТПРАВИТЕЛЬ")
        field("Отправитель", "sender")
        titleText("ГРУЗ")
        placesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(placesBox)
        totals = TextView(this).apply { textSize = 17f; setPadding(0,16,0,16) }
        root.addView(totals)
        button("Добавить грузовое место") { addPlace() }
        button("Сохранить PDF") { savePdf() }
        button("Отправить последний PDF") { shareLast() }
        restoring = true
        try {
            val data = JSONObject(getPreferences(MODE_PRIVATE).getString("draft", "{}") ?: "{}")
            fields.forEach { (k,v) -> v.setText(data.optString(k)) }
            val arr = data.optJSONArray("places")
            if (arr != null) for (i in 0 until arr.length()) addPlace(arr.optJSONObject(i))
        } catch (_: Exception) {}
        if (places.isEmpty()) addPlace()
        restoring = false
        update()
    }
    private fun titleText(t: String) {
        root.addView(TextView(this).apply { text=t; textSize=19f; setTypeface(null, Typeface.BOLD); setPadding(0,20,0,8) })
    }
    private fun edit(parent: LinearLayout, hintText: String, value: String = ""): EditText {
        val e = EditText(this).apply { hint=hintText; setSingleLine(true); setText(value); textSize=16f }
        parent.addView(e, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        e.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start:Int, count:Int, after:Int) {}
            override fun onTextChanged(s:CharSequence?, start:Int, before:Int, count:Int) { update() }
            override fun afterTextChanged(s:Editable?) {}
        })
        return e
    }
    private fun field(label: String, key: String) { fields[key] = edit(root,label) }
    private fun button(t:String, action:()->Unit) {
        root.addView(Button(this).apply { text=t; setOnClickListener { action() } })
    }
    private fun addPlace(data: JSONObject? = null) {
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(12,12,12,12); setBackgroundColor(0xffeeeeee.toInt()) }
        placesBox.addView(box,LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=12 })
        box.addView(TextView(this).apply { text="Место №${places.size+1}"; textSize=17f })
        val p=Place(edit(box,"Наименование груза",data?.optString("name")?:""),
            edit(box,"Вес, кг",data?.optString("weight")?:""),
            edit(box,"Длина, см",data?.optString("length")?:""),
            edit(box,"Ширина, см",data?.optString("width")?:""),
            edit(box,"Высота, см",data?.optString("height")?:""),box)
        places.add(p)
        box.addView(Button(this).apply { text="Удалить место"; setOnClickListener {
            places.remove(p); placesBox.removeView(box)
            places.forEachIndexed { i, place -> (place.container.getChildAt(0) as TextView).text="Место №${i+1}" }
            update()
        } })
        update()
    }
    private fun number(e: EditText): Double = e.text.toString().replace(',','.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    private fun volume(p: Place) = number(p.length)*number(p.width)*number(p.height)/1000000.0
    private fun format(n:Double)=String.format(Locale.US,"%.3f",n)
    private fun update() {
        if (!::totals.isInitialized || restoring) return
        totals.text="Мест: ${places.size}    Вес: ${format(places.sumOf { number(it.weight) })} кг\nОбщая кубатура: ${format(places.sumOf { volume(it) })} м³"
        val json=JSONObject()
        fields.forEach { (k,v)->json.put(k,v.text.toString()) }
        val arr=JSONArray()
        places.forEach { p->arr.put(JSONObject().put("name",p.name.text.toString()).put("weight",p.weight.text.toString()).put("length",p.length.text.toString()).put("width",p.width.text.toString()).put("height",p.height.text.toString())) }
        json.put("places",arr)
        getPreferences(MODE_PRIVATE).edit().putString("draft",json.toString()).apply()
    }
    private fun savePdf() {
        try {
            val doc=PdfDocument()
            var pageNo=0
            var page:PdfDocument.Page?=null
            var y=40f
            val regular=Paint(3).apply { color=android.graphics.Color.BLACK; textSize=12f; typeface=Typeface.create("sans-serif",Typeface.NORMAL) }
            val heavy=Paint(regular).apply { typeface=Typeface.create("sans-serif",Typeface.BOLD) }
            val logo = BitmapFactory.decodeResource(resources, R.drawable.fastrance_logo)
            fun newPage() {
                if (page != null) doc.finishPage(page!!)
                pageNo++
                page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create())
                val logoWidth = 170f
                val logoHeight = logo.height * logoWidth / logo.width
                page!!.canvas.drawBitmap(logo, null, RectF(40f, 40f, 40f + logoWidth, 40f + logoHeight), null)
                y=40f + logoHeight + 24f
            }
            fun write(s:String,bold:Boolean=false) {
                if (page==null || y>790f) newPage()
                val paint=if(bold) heavy else regular
                var text=s
                while(text.isNotEmpty()) {
                    if(y>790f) newPage()
                    var cut=paint.breakText(text,true,515f,null)
                    if(cut<=0) cut=1
                    page!!.canvas.drawText(text.substring(0,cut),40f,y,paint)
                    text=text.substring(cut)
                    y+=19f
                }
            }
            newPage()
            write("ЗАЯВЛЕНИЕ НА ОТПРАВКУ ГРУЗА",true)
            write("Дата: "+SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(Date()))
            val blankNumber=fields["number"]!!.text.toString()
            if(blankNumber.isNotBlank()) write("Номер: $blankNumber")
            for ((key,label) in listOf("city" to "Город", "destination" to "Назначение", "recipient" to "Получатель", "sender" to "Отправитель")) {
                write("$label: ${fields[key]!!.text}")
            }
            write("ГРУЗОВЫЕ МЕСТА",true)
            places.forEachIndexed { i,p ->
                write("${i+1}. ${p.name.text.ifBlank { "Груз" }}",true)
                write("Вес: ${format(number(p.weight))} кг")
                write("Габариты: ${p.length.text} x ${p.width.text} x ${p.height.text} см")
                write("Объём: ${format(volume(p))} м³")
            }
            write("ИТОГО МЕСТ: ${places.size}",true)
            write("ОБЩИЙ ВЕС: ${format(places.sumOf { number(it.weight) })} кг",true)
            write("ОБЩАЯ КУБАТУРА: ${format(places.sumOf { volume(it) })} м³",true)
            write("Подпись отправителя: ___________________")
            write("Подпись получателя: ___________________")
            doc.finishPage(page!!)
            val name="GruzBlank_"+SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())+".pdf"
            val values=ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME,name)
                put(MediaStore.Downloads.MIME_TYPE,"application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH,"Download/GruzBlank")
                put(MediaStore.Downloads.IS_PENDING,1)
            }
            val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error("Не удалось создать PDF")
            contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) } ?: error("Не удалось записать PDF")
            doc.close()
            contentResolver.update(uri,ContentValues().apply { put(MediaStore.Downloads.IS_PENDING,0) },null,null)
            getPreferences(MODE_PRIVATE).edit().putString("lastPdf",uri.toString()).apply()
            Toast.makeText(this,"PDF сохранён в Загрузки/GruzBlank",Toast.LENGTH_LONG).show()
        } catch(e:Exception) { AlertDialog.Builder(this).setMessage("Ошибка PDF: ${e.message}").setPositiveButton("OK",null).show() }
    }
    private fun shareLast() {
        val saved=getPreferences(MODE_PRIVATE).getString("lastPdf",null)
        if(saved==null) { Toast.makeText(this,"Сначала сохраните PDF",Toast.LENGTH_SHORT).show();return }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type="application/pdf"
            putExtra(Intent.EXTRA_STREAM,Uri.parse(saved))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },"Отправить PDF"))
    }
}
