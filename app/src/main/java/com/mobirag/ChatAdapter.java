package com.mobirag;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import mobirag.R;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private final List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        LinearLayout layout;

        ViewHolder(View view) {
            super(view);
            textView = view.findViewById(R.id.chatMessageText);
            layout = view.findViewById(R.id.chatMessageLayout);
        }
    }

    @Override
    public ChatAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.chat_message_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ChatAdapter.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        holder.textView.setText(msg.text);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(16, 8, 16, 8);

        if (msg.isUser) {
            params.gravity = Gravity.END;
            holder.textView.setBackgroundResource(R.drawable.bg_user_message);
        } else {
            params.gravity = Gravity.START;
            holder.textView.setBackgroundResource(R.drawable.bg_ai_message);
            if (msg.text.startsWith("Thinking")) {
                holder.textView.setTextColor(Color.parseColor("#888888"));
                holder.textView.setTypeface(null, Typeface.ITALIC);
            } else {
                holder.textView.setTextColor(Color.BLACK);
                holder.textView.setTypeface(null, Typeface.NORMAL);
            }
        }

        holder.layout.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }
}

