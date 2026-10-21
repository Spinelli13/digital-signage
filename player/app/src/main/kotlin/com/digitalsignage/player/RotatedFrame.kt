package com.digitalsignage.player

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import com.digitalsignage.player.data.ScreenOrientation

/**
 * Desenha o único filho girado em [degrees] (múltiplos de 90, sentido horário), ocupando a tela inteira: em 90° e 270° o
 * filho recebe a largura e a altura trocadas e fica centralizado. É assim que uma TV instalada na vertical mostra o
 * conteúdo de pé mesmo recebendo um sinal de vídeo horizontal.
 */
class RotatedFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    var degrees: Int = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            // Quem vai começar a exibir logo depois (zonas dimensionadas pelo tamanho da tela) precisa já do tamanho novo.
            if (width > 0 && height > 0) {
                measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
                layout(left, top, right, bottom)
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val child: View = getChildAt(0) ?: return
        val swapped = ScreenOrientation.swapsAxes(degrees)
        child.measure(
            MeasureSpec.makeMeasureSpec(if (swapped) height else width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(if (swapped) width else height, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child: View = getChildAt(0) ?: return
        val childLeft = ((right - left) - child.measuredWidth) / 2
        val childTop = ((bottom - top) - child.measuredHeight) / 2
        child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)
        child.pivotX = child.measuredWidth / 2f
        child.pivotY = child.measuredHeight / 2f
        child.rotation = degrees.toFloat()
    }
}
