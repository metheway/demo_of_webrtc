package utils;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.baidu.demo.R;

import org.webrtc.SurfaceViewRenderer;

import java.util.Map;

public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.MyViewHolder> {
    private String TAG = RecyclerAdapter.class.getCanonicalName();
    private Map<Integer, SurfaceViewRenderer> surfaceViewRendererMap;
    private Context mContext;

    public RecyclerAdapter(Map<Integer, SurfaceViewRenderer> surfaceViewRendererMap, Context mContext) {
        this.surfaceViewRendererMap = surfaceViewRendererMap;
        this.mContext = mContext;
    }

    // 这里重新出现的话需要放入流，也就是释放掉的surfaceView需要重新显示的话，需要加入流，暂时不写，以后补
    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_recycler_view, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        // 当刷到这个view的时候，要绑定，这个position和id是否一致要检查
        surfaceViewRendererMap.put(position, holder.surfaceViewRenderer);
        Log.i(TAG, "findView" + String.valueOf(holder.surfaceViewRenderer));
        // 本来外面放入的是空的
    }


    @Override
    public int getItemCount() {
        // 通过这个调节找到每行
        return surfaceViewRendererMap.size();
    }

    class MyViewHolder extends RecyclerView.ViewHolder {
        // 实例化三个surfaceView
        private SurfaceViewRenderer surfaceViewRenderer;

        public MyViewHolder(View itemView) {
            super(itemView);
            surfaceViewRenderer = itemView.findViewById(R.id.surface_view);
        }
    }
}
