package com.cs446.group18.timetracker.ui;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cs446.group18.timetracker.R;
import com.cs446.group18.timetracker.adapter.EventListAdapter;
import com.cs446.group18.timetracker.adapter.IconListAdaptor;
import com.cs446.group18.timetracker.entity.Event;
import com.cs446.group18.timetracker.entity.Geolocation;
import com.cs446.group18.timetracker.entity.TimeEntry;
import com.cs446.group18.timetracker.utils.AbstractFactory;
import com.cs446.group18.timetracker.utils.ConcreteFactory;
import com.cs446.group18.timetracker.utils.OpenAIDescriptionGenerator;
import com.cs446.group18.timetracker.vm.EventListViewModelFactory;
import com.cs446.group18.timetracker.vm.EventViewModel;
import com.cs446.group18.timetracker.vm.GeolocationViewModel;
import com.cs446.group18.timetracker.vm.GeolocationViewModelFactory;
import com.cs446.group18.timetracker.vm.TimeEntryListViewModelFactory;
import com.cs446.group18.timetracker.vm.TimeEntryViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EventListFragment extends Fragment implements EventListAdapter.OnEventListener, FragmentDatabaseSaver {
    private EventListAdapter adapter;
    private EventListAdapter searchAdaptor;
    private List<Event> events = new ArrayList<>();
    RecyclerView recyclerView;
    private TextView textViewEmpty;
    private FloatingActionButton buttonAddEvent;
    private int position;
    private int prevPosition;
    private final ArrayList<Integer> iconList = new ArrayList<>(Arrays.asList(R.drawable.ic_cooking, R.drawable.ic_yoga, R.drawable.ic_homework, R.drawable.ic_movies, R.drawable.ic_music, R.drawable.ic_soccer,
            R.drawable.ic_gym, R.drawable.ic_cafe, R.drawable.ic_hospital, R.drawable.ic_coffee, R.drawable.ic_shopping_cart, R.drawable.ic_task, R.drawable.ic_television, R.drawable.ic_youtube, R.drawable.ic_television, R.drawable.ic_compass, R.drawable.ic_email));
    private AbstractFactory factory = new ConcreteFactory();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View eventListView = inflater.inflate(R.layout.fragment_event_list, container, false);

        EventListAdapter eventListAdapter = new EventListAdapter(events, this);

        this.searchAdaptor = eventListAdapter;

        EventListAdapter adapter = new EventListAdapter(events, this);
        this.adapter = adapter;
        EventListViewModelFactory eventListViewModelFactory = factory.provideEventListViewModelFactory(getActivity());
        EventViewModel viewModel = new ViewModelProvider(this, eventListViewModelFactory).get(EventViewModel.class);

        textViewEmpty = eventListView.findViewById(R.id.empty_event_list);
        recyclerView = eventListView.findViewById(R.id.event_list);
        recyclerView.setAdapter(eventListAdapter);
        setHasOptionsMenu(true);


        // Add New Event
        buttonAddEvent = eventListView.findViewById(R.id.button_add_event);
        buttonAddEvent.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                View promptView = inflater.inflate(R.layout.prompt_add_event, container, false);
                //create icon list view
                final GridView list = promptView.findViewById(R.id.iconList);
                IconListAdaptor iconAdapter = new IconListAdaptor(promptView.getContext(), R.layout.list_item_icon, iconList);
                list.setAdapter(iconAdapter);

                final EditText eventNameText = promptView.findViewById(R.id.event_name);
                builder.setView(promptView)
                        .setPositiveButton("OK", null)
                        .setNegativeButton("Cancel",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                });

                AlertDialog dialog = builder.create();
                dialog.setOnShowListener(dialogInterface -> {
                    final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    positiveButton.setOnClickListener(view1 -> {
                        String eventName = eventNameText.getText().toString().trim();
                        if (TextUtils.isEmpty(eventName)) {
                            eventNameText.setError(getString(R.string.error_event_name_required));
                            return;
                        }

                        int selectedIcon = iconAdapter.getSelected();
                        if (selectedIcon == -1 && !iconList.isEmpty()) {
                            selectedIcon = iconList.get(0);
                        }

                        if (getContext() == null) {
                            positiveButton.setEnabled(true);
                            return;
                        }

                        positiveButton.setEnabled(false);
                        Toast.makeText(getContext(), getString(R.string.toast_generating_event_description), Toast.LENGTH_SHORT).show();

                        final int resolvedIcon = selectedIcon;
                        final String finalEventName = eventName;
                        OpenAIDescriptionGenerator.generateDescription(eventName, description -> {
                            if (!isAdded()) {
                                positiveButton.setEnabled(true);
                                return;
                            }

                            Event event = new Event(finalEventName, description, resolvedIcon);
                            viewModel.insert(event);

                            if (getContext() != null) {
                                Toast.makeText(getContext(), getString(R.string.toast_event_created, finalEventName), Toast.LENGTH_SHORT).show();
                            }

                            dialog.dismiss();
                        });
                    });
                });
                dialog.show();
            }
        });


        // Swipe Right to Delete Event & Swipe Left to Update
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (direction == ItemTouchHelper.RIGHT) {
                    viewModel.delete(eventListAdapter.getEventAt(viewHolder.getAdapterPosition()));
                    Toast.makeText(eventListView.getContext(), "Event Deleted", Toast.LENGTH_SHORT).show();
                } else if (direction == ItemTouchHelper.LEFT) {
                    long eventId = eventListAdapter.getEventAt(viewHolder.getAdapterPosition()).getEventId();
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    View promptView = inflater.inflate(R.layout.prompt_add_event, container, false);

                    //create icon list view
                    final GridView list = promptView.findViewById(R.id.iconList);
                    IconListAdaptor iconAdapter = new IconListAdaptor(promptView.getContext(), R.layout.list_item_icon, iconList);
                    list.setAdapter(iconAdapter);


                    final EditText eventNameText = promptView.findViewById(R.id.event_name);
                    Event existingEvent = eventListAdapter.getEventAt(viewHolder.getAdapterPosition());
                    if (existingEvent != null) {
                        eventNameText.setText(existingEvent.getEventName());
                    }
                    builder.setView(promptView)
                            .setPositiveButton("OK", null)
                            .setNegativeButton("Cancel",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            TextView eventNameTextView = viewHolder.itemView.findViewById(R.id.text_view_title);
                                            String eventName = eventNameTextView.getText().toString();
                                            TextView eventDescriptionTextView = viewHolder.itemView.findViewById(R.id.text_view_description);
                                            String eventDescription = eventDescriptionTextView.getText().toString();
                                            // get selected icon
                                            Event temp = eventListAdapter.getEventAt(viewHolder.getAdapterPosition());
                                            int iconId = temp.getIcon();
                                            Event event = new Event(eventName, eventDescription, iconId);
                                            event.setEventId(eventId);
                                            viewModel.update(event);
                                            dialog.cancel();
                                        }
                                    });

                    AlertDialog dialog = builder.create();
                    dialog.setOnShowListener(dialogInterface -> {
                        final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        positiveButton.setOnClickListener(view1 -> {
                            String eventName = eventNameText.getText().toString().trim();
                            if (TextUtils.isEmpty(eventName)) {
                                eventNameText.setError(getString(R.string.error_event_name_required));
                                return;
                            }

                            int selectedIcon = iconAdapter.getSelected();
                            if (selectedIcon == -1) {
                                Event currentEvent = eventListAdapter.getEventAt(viewHolder.getAdapterPosition());
                                if (currentEvent != null) {
                                    selectedIcon = currentEvent.getIcon();
                                } else if (!iconList.isEmpty()) {
                                    selectedIcon = iconList.get(0);
                                }
                            }

                            if (getContext() == null) {
                                positiveButton.setEnabled(true);
                                return;
                            }

                            positiveButton.setEnabled(false);
                            Toast.makeText(getContext(), getString(R.string.toast_generating_event_description), Toast.LENGTH_SHORT).show();

                            final int resolvedIcon = selectedIcon;
                            final String finalEventName = eventName;
                            OpenAIDescriptionGenerator.generateDescription(eventName, description -> {
                                if (!isAdded()) {
                                    positiveButton.setEnabled(true);
                                    return;
                                }

                                Event event = new Event(finalEventName, description, resolvedIcon);
                                event.setEventId(eventId);
                                viewModel.update(event);

                                if (getContext() != null) {
                                    Toast.makeText(getContext(), getString(R.string.toast_event_updated, finalEventName), Toast.LENGTH_SHORT).show();
                                }

                                dialog.dismiss();
                            });
                        });
                    });
                    dialog.show();
                }

            }
        }).attachToRecyclerView(recyclerView);


        subscribeUI(eventListAdapter);
        TimeEntryListViewModelFactory timeEntryListViewModelFactory = factory.provideTimeEntryListViewModelFactory(getActivity());
        timeEntryViewModel = new ViewModelProvider(this, timeEntryListViewModelFactory).get(TimeEntryViewModel.class);
        GeolocationViewModelFactory geolocationViewModelFactory = factory.provideGeolocationViewModelFactory(getActivity());
        geolocationViewModel = new ViewModelProvider(this, geolocationViewModelFactory).get(GeolocationViewModel.class);
        return eventListView;
    }

    private void subscribeUI(EventListAdapter eventListAdapter) {

        EventListViewModelFactory eventListViewModelFactory = factory.provideEventListViewModelFactory(getActivity());
        EventViewModel viewModel = new ViewModelProvider(this, eventListViewModelFactory).get(EventViewModel.class);
        viewModel.getEvents().observe(getViewLifecycleOwner(), new Observer<List<Event>>() {
            @Override
            public void onChanged(@Nullable List<Event> events) {

                if (events != null && !events.isEmpty()) {
                    recyclerView.setVisibility(View.VISIBLE);
                    textViewEmpty.setVisibility(View.GONE);
                } else {
                    recyclerView.setVisibility(View.GONE);
                    textViewEmpty.setVisibility(View.VISIBLE);
                }
                setEvents(events);
                eventListAdapter.setEvents(events);
            }
        });
    }


    // Expandable CardView
    @SuppressLint("RestrictedApi")
    @Override
    public void onEventClick(int position, boolean isFromNFC) {

        setRecyclerView(recyclerView);
        this.position = position;
        // Time Entries
        long eventID = events.get(position).getEventId();
        boolean isFragmentAlreadyAVailable = false;
        for (Fragment fragment : getChildFragmentManager().getFragments()) {
            if (fragment instanceof StopwatchFragment) {
                isFragmentAlreadyAVailable = true;
            }
        }
        if (!isFragmentAlreadyAVailable) {
            //integration to stopwatch, should be changed later
            FragmentTransaction ft = getChildFragmentManager().beginTransaction();
            StopwatchFragment stopwatchFragment = StopwatchFragment.newInstance(eventID, isFromNFC);
            ft.replace(R.id.stopwatch, stopwatchFragment);
            ft.commit();
        } else {
        }

        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        View view = linearLayoutManager.findViewByPosition(position);
        LinearLayout expandableLinearLayout = view.findViewById(R.id.expandable);

        View preView = linearLayoutManager.findViewByPosition(prevPosition);
        LinearLayout preExpandableLinearLayout = view.findViewById(R.id.expandable);


        TimeEntryListViewModelFactory timeEntryListViewModelFactory = factory.provideTimeEntryListViewModelFactory((getActivity()));
        TimeEntryViewModel timeEntryViewModel = new ViewModelProvider(this, timeEntryListViewModelFactory).get(TimeEntryViewModel.class);
        timeEntryViewModel.getTimeEntriesByEventID(eventID).observe(getViewLifecycleOwner(), new Observer<List<TimeEntry>>() {
            @Override
            public void onChanged(List<TimeEntry> timeEntries) {
                if (expandableLinearLayout.getChildCount() > 0) {
                    expandableLinearLayout.removeAllViews();
                }
                for (int i = 0; i < timeEntries.size(); ++i) {
                    TimeEntry entry = timeEntries.get(i);
                    SimpleDateFormat datetimeformat = new SimpleDateFormat("MM/dd/yyyy HH:mm");
                    SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm");
                    String text = datetimeformat.format(entry.getStartTime()) + " to " + timeformat.format(entry.getEndTime());
                    TextView textView = new TextView(getContext());
                    textView.setText(text);
                    textView.setId(i);
                    expandableLinearLayout.addView(textView);
                }
                adapter.setTimeEntries(timeEntries);
            }
        });
        if (expandableLinearLayout.getVisibility() == View.GONE) {
            expand(expandableLinearLayout);

            prevPosition = position;
            buttonAddEvent.setVisibility(View.GONE);

//            stopwatchFragment.startPlayButton();
        } else {
            if (isFromNFC)
                if (getChildFragmentManager() != null && getChildFragmentManager().getFragments() != null)
                    for (Fragment fragment : getChildFragmentManager().getFragments()) {
                        if (fragment instanceof StopwatchFragment) {
                            StopwatchFragment fragmentSTp = ((StopwatchFragment) fragment);
                            if (fragmentSTp.getTimerRunningState())
                                fragmentSTp.stopPlayButton();
                            else fragmentSTp.startPlayButton();
                        }
                    }
            //            stopwatchFragment.stopPlayButton();
            if (!isFromNFC) {
                FragmentTransaction closeFt = getChildFragmentManager().beginTransaction();
                for (Fragment fragment : getChildFragmentManager().getFragments())
                    if (fragment instanceof StopwatchFragment) {
                        closeFt.remove(fragment).commit();
                    }

                if (expandableLinearLayout.getChildCount() > 0) {
                    expandableLinearLayout.removeAllViews();
                }
                collapse(expandableLinearLayout);
                buttonAddEvent.setVisibility(View.VISIBLE);
            }
        }

    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
        inflater.inflate(R.menu.menu_search, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchAdaptor.getFilter().filter(newText);
                return false;
            }
        });
    }


    private void expand(LinearLayout layout) {
        layout.setVisibility(View.VISIBLE);
    }


    public void collapse(LinearLayout layout) {
        layout.setVisibility(View.GONE);
    }

    private void setEvents(List<Event> events) {
        this.events = events;
    }

    public int getPosition() {
        return this.position;
    }

    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    public void setRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    public void receivedNFCTagEvent(String eventName) {
        if (events != null)
            for (int i = 0; i < events.size(); i++)
                if (events.get(i).getEventName() != null && events.get(i).getEventName().equalsIgnoreCase(eventName)) {
                    onEventClick(i, true);
                    break;
                }
    }

    private TimeEntryViewModel timeEntryViewModel;
    private GeolocationViewModel geolocationViewModel;

    @Override
    public void updateGeoLocation(Geolocation geolocation) {
        geolocationViewModel.insert(geolocation);
    }

    @Override
    public long updateTimeEntry(TimeEntry timeEntry) {
        return timeEntryViewModel.insert(timeEntry);
    }
}
