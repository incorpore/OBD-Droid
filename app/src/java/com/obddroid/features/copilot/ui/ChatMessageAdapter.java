package com.obddroid.features.copilot.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.obddroid.R;
import com.obddroid.features.copilot.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.Markwon;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {

    public interface OnSpeakerClickListener {
        void onSpeakerClick(ChatMessage message, ImageButton speakerButton);
    }

    private final List<ChatMessage> messages = new ArrayList<>();
    private final Markwon markwon;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private OnSpeakerClickListener speakerClickListener;

    public ChatMessageAdapter(Markwon markwon) {
        this.markwon = markwon;
    }

    public void setSpeakerClickListener(OnSpeakerClickListener listener) {
        this.speakerClickListener = listener;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void clearMessages() {
        int size = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, size);
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {

        private final CardView userMessageCard;
        private final CardView aiMessageCard;
        private final TextView userMessageText;
        private final TextView userMessageTime;
        private final TextView aiMessageText;
        private final TextView aiMessageTime;
        private final ImageButton aiMessageSpeaker;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            userMessageCard = itemView.findViewById(R.id.user_message_card);
            aiMessageCard = itemView.findViewById(R.id.ai_message_card);
            userMessageText = itemView.findViewById(R.id.user_message_text);
            userMessageTime = itemView.findViewById(R.id.user_message_time);
            aiMessageText = itemView.findViewById(R.id.ai_message_text);
            aiMessageTime = itemView.findViewById(R.id.ai_message_time);
            aiMessageSpeaker = itemView.findViewById(R.id.ai_message_speaker);
        }

        void bind(ChatMessage message) {
            String timeStr = timeFormat.format(new Date(message.getTimestamp()));

            if (message.isFromUser()) {
                // Show user message
                userMessageCard.setVisibility(View.VISIBLE);
                aiMessageCard.setVisibility(View.GONE);
                userMessageText.setText(message.getContent());
                userMessageTime.setText(timeStr);
            } else {
                // Show AI message with markdown rendering
                userMessageCard.setVisibility(View.GONE);
                aiMessageCard.setVisibility(View.VISIBLE);

                // Render markdown for AI responses
                markwon.setMarkdown(aiMessageText, message.getContent());
                aiMessageTime.setText(timeStr);

                // Setup speaker button
                aiMessageSpeaker.setOnClickListener(v -> {
                    if (speakerClickListener != null) {
                        speakerClickListener.onSpeakerClick(message, aiMessageSpeaker);
                    }
                });
            }
        }
    }
}
