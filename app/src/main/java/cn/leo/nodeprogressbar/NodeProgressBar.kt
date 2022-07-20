package cn.leo.nodeprogressbar

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.core.view.doOnLayout
import kotlin.math.roundToInt

/**
 * 带节点的进度条
 * @author leo
 * 2022-7-20
 */
/**
<declare-styleable name="NodeProgressBar">
<attr name="npb_thickness" format="dimension" />
<attr name="npb_start_color" format="color" />
<attr name="npb_end_color" format="color" />
<attr name="npb_background_color" format="color" />
<attr name="npb_node_active" format="reference" />
<attr name="npb_node_inactive" format="reference" />
<attr name="npb_text_size" format="dimension" />
<attr name="npb_text_color" format="color" />
<attr name="npb_text_margin" format="dimension" />
<attr name="npb_text_align" format="enum">
<enum name="top" value="1" />
<enum name="bottom" value="2" />
</attr>
</declare-styleable>
 */
class NodeProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var mNodes: Array<Node>? = null
    private var mForegroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mBackgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mTextPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var mBackgroundColor = Color.LTGRAY
    private var mStartColor = Color.GREEN
    private var mEndColor = Color.GREEN
    private var mNodeActive = -1
    private var mNodeInactive = -1
    private var mTextSize = 16f.sp
    private var mTextColor = Color.BLACK
    private var mTextMargin = 8.dp
    private var mTextAlign = 2
    private var mWidth = 0
    private var mHeight = 0
    private var mThickness = 10.dp
    private var mProgress = 0
    private var mPart = 0f
    private var mForeground: LinearGradient? = null
    private var mBitmapCache = mutableMapOf<Int, Bitmap>()

    private val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density).roundToInt()
    private val Float.sp: Float
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            this,
            resources.displayMetrics
        )

    init {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.NodeProgressBar)
        mThickness = typedArray.getDimension(
            R.styleable.NodeProgressBar_npb_thickness,
            mThickness.toFloat()
        ).roundToInt()
        mStartColor = typedArray.getColor(R.styleable.NodeProgressBar_npb_start_color, mStartColor)
        mEndColor = typedArray.getColor(R.styleable.NodeProgressBar_npb_end_color, mEndColor)
        mNodeActive = typedArray.getResourceId(R.styleable.NodeProgressBar_npb_node_active, -1)
        mNodeInactive = typedArray.getResourceId(R.styleable.NodeProgressBar_npb_node_inactive, -1)
        mTextSize = typedArray.getDimension(R.styleable.NodeProgressBar_npb_text_size, mTextSize)
        mTextColor = typedArray.getColor(R.styleable.NodeProgressBar_npb_text_color, mTextColor)
        mTextMargin = typedArray.getDimension(
            R.styleable.NodeProgressBar_npb_text_margin,
            mTextMargin.toFloat()
        ).roundToInt()
        mTextAlign = typedArray.getInt(R.styleable.NodeProgressBar_npb_text_align, mTextAlign)
        typedArray.recycle()
        if (mNodeActive != -1) {
            val bitmap = BitmapFactory.decodeResource(resources, mNodeActive)
            mBitmapCache[mNodeActive] = bitmap
        }
        if (mNodeInactive != -1) {
            val bitmap = BitmapFactory.decodeResource(resources, mNodeInactive)
            mBitmapCache[mNodeInactive] = bitmap
        }
        //文字
        mTextPaint.textAlign = Paint.Align.CENTER
        mTextPaint.textSize = mTextSize
        mTextPaint.color = mTextColor
        doOnLayout {
            if (isInEditMode) {
                setNodes(arrayOf(Node("节点1"), Node("节点2"), Node("节点3")))
                setProgress(2)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        mWidth = MeasureSpec.getSize(widthMeasureSpec)
        mHeight = MeasureSpec.getSize(heightMeasureSpec)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (mNodes != null) {
            setNodes(mNodes)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val centerY = mHeight / 2
        val length = mProgress * mPart - mPart / 2f
        val offset = if (mProgress > 0) {
            mNodes?.get(mProgress - 1)?.offset ?: 0
        } else {
            0
        }
        val left = 0f
        val top = centerY - mThickness / 2f
        val right = length + offset
        val bottom = centerY + mThickness / 2f
        //绘制进度条背景
        mBackgroundPaint.color = mBackgroundColor
        canvas.drawRect(left, top, mWidth.toFloat(), bottom, mBackgroundPaint)
        //绘制点亮的进度
        mForegroundPaint.color = mStartColor
        mForegroundPaint.shader = mForeground
        canvas.drawRect(left, top, right, bottom, mForegroundPaint)
        val nodes = mNodes ?: return
        //绘制节点
        for (i in nodes.indices) {
            val node = nodes[i]
            val x = (i + 1) * mPart - mPart / 2
            val bitmap = getNodeBitmap(node, mProgress > i)
            if (bitmap != null) {
                val l = x - (bitmap.width / 2f)
                val t = centerY - (bitmap.height / 2f)
                canvas.drawBitmap(bitmap, l, t, mForegroundPaint)
            }
            //绘制节点文字
            val margin = if (mTextAlign == 2) {
                centerY + node.offset + mTextMargin
            } else {
                centerY - node.offset - mTextMargin
            }
            canvas.drawText(node.text, x, margin.toFloat(), mTextPaint)
        }
    }

    private fun getNodeBitmap(node: Node, isActive: Boolean): Bitmap? {
        val resId = if (isActive) {
            if (node.activeDrawable == -1) mNodeActive else node.activeDrawable
        } else {
            if (node.inactiveDrawable == -1) mNodeInactive else node.inactiveDrawable
        }
        if (resId == -1) return null
        var bitmap = mBitmapCache[resId]
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeResource(resources, resId)
            mBitmapCache[resId] = bitmap
        }
        return bitmap
    }

    /**
     * 传入节点
     *
     * @param nodes
     */
    fun setNodes(nodes: Array<Node>?) {
        require(!(nodes == null || nodes.isEmpty())) { "nodes is empty" }
        mNodes = nodes
        //分段长度
        mPart = (mWidth / nodes.size).toFloat()
        postInvalidate()
    }

    /**
     * 完成度，进度高亮到节点
     *
     * @param progress 0 - mNodes.length
     */
    fun setProgress(progress: Int) {
        require(!(mNodes == null || mNodes!!.isEmpty())) { "nodes is empty" }
        mProgress = progress
        val centerY = mHeight / 2
        val length = mProgress * mPart - mPart / 2f
        val offset = mNodes?.get(mProgress)?.offset ?: 0
        val left = 0f
        val top = centerY - mThickness / 2f
        val right = length + offset
        val bottom = centerY + mThickness / 2f
        mForeground =
            LinearGradient(left, top, right, bottom, mStartColor, mEndColor, Shader.TileMode.CLAMP)
        postInvalidate()
    }

    @Keep
    data class Node(
        //节点文字
        val text: String = "",
        //节点左右偏移
        val offset: Int = 0,
        //节点激活的图片
        @DrawableRes
        val activeDrawable: Int = -1,
        //节点没激活的图片
        @DrawableRes
        val inactiveDrawable: Int = -1
    )
}