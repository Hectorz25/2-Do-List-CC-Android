package com.examenconcredito.a2_dolistapp.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.examenconcredito.a2_dolistapp.R
import com.examenconcredito.a2_dolistapp.data.database.AppDatabase
import com.examenconcredito.a2_dolistapp.data.entities.TaskListEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskListAdapter(
    private val taskLists: List<TaskListEntity>,
    private val onItemClick: (TaskListEntity) -> Unit,
    private val onDeleteClick: (TaskListEntity) -> Unit,
    private val db: AppDatabase
) : RecyclerView.Adapter<TaskListAdapter.TaskListViewHolder>() {

    inner class TaskListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvListTitle: TextView = itemView.findViewById(R.id.tvListTitle)
        private val tvTaskCount: TextView = itemView.findViewById(R.id.tvTaskCount)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteList)

        fun bind(taskList: TaskListEntity) {
            tvListTitle.text = taskList.title

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val tasks = db.taskDao().getTasksByList(taskList.id)
                    val completedCount = tasks.count { it.isCompleted }
                    val totalCount = tasks.size

                    withContext(Dispatchers.Main) {
                        tvTaskCount.text = itemView.context.getString(
                            R.string.task_count_format,
                            completedCount,
                            totalCount
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvTaskCount.text = itemView.context.getString(R.string.task_count_format, 0, 0)
                    }
                }
            }

            itemView.setOnClickListener { onItemClick(taskList) }
            btnDelete.setOnClickListener { onDeleteClick(taskList) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task_list, parent, false)
        return TaskListViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskListViewHolder, position: Int) {
        holder.bind(taskLists[position])
    }

    override fun getItemCount(): Int = taskLists.size
}