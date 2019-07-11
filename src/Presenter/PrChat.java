package Presenter;

import Model.Model;
import Utils.DateConverter;
import View.Forms.ViewChat;
import View.ListItem.ContactItem;
import View.ListItem.MessageItem;
import View.ListRenderer.ListCellRendererContact;
import org.javagram.handlers.IncomingMessageHandler;
import org.javagram.response.MessagesSentMessage;
import org.javagram.response.object.*;
import org.javagram.response.object.Dialog;
import org.telegram.api.TLMessage;
import org.telegram.api.TLPeerUser;

import javax.swing.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

public class PrChat implements IPresenter, IncomingMessageHandler {
    Model model;
    ViewChat view;
    private User user;
//    private HashMap<Integer, User> contacts; // Int - userID
//    private HashMap<Integer, LinkedList<Message>> messages; // Int - userID
    private ArrayList<Dialog> dialogs;      // диалоги для заполнения списка контактов
    private Random random;
    private State state;

    public PrChat(ViewChat view) {
        this.view = view;
        this.model = Model.getInstance();
        this.user = model.getSelfUser();
        this.random = new Random();
        model.setMessageHandler(this);
        this.state = model.getState();

        // Имя пользователя - метка сверху над чатом
        view.getUserNameLabel().setText(user.getFirstName() + " " + user.getLastName());

        Thread contactListThread = new Thread(new Runnable() {
            @Override
            public void run() {
                setupContactList();
            }
        });
        contactListThread.start();
    }



    public ViewChat getView() {
        return view;
    }


    /** Получение диалогов и последних сообщений от пользователей
     *  На основании сообщений, упорядоченных по убыванию даты (делает Telegram)
     *  формируем список контактов
     *  */
    private void setupContactList() {
        // Получение диалогов
        dialogs = model.getDialogs();
        // Получение ID последних сообщений из списка диалогов
        ArrayList<Integer> messageIds = new ArrayList<>();
        for (Dialog dialog : dialogs) {
            if(dialog != null)
                messageIds.add(dialog.getTopMessage());
        }

        // Последние сообщения на основании ID сообщения
        ArrayList<Message> topMessages = model.getMessagesById(messageIds);

        // Получить ID собеседников из последних сообщений
        ArrayList<Integer> userIds = new ArrayList<>();
        for (Message msg : topMessages) {
            if (msg.getToId() == 0 || msg.getFromId() == 0) {
                continue;
            } else if (user.getId() == msg.getFromId()) {
                userIds.add(msg.getToId());
            } else {
                userIds.add(msg.getFromId());
            }
            System.err.println("Message: " + msg.getMessage().replace("\n", " ") + "\nFrom: " + msg.getFromId() + "\tTo: " + msg.getToId() );
        }
        System.err.println("------------------------------------------------\n");

        // Получить список User по списку ID
        ArrayList<User> contactUsers = model.getUsersById(userIds);

        // Сформировать модель контактов из ContactItem() и Список истории в модели
        DefaultListModel<ContactItem> modelContacts = new DefaultListModel<>();
        for (int i = 0; i < contactUsers.size(); i++ ) {
            User user = contactUsers.get(i);
            if (user.getId() == 0) continue;
            System.err.println("ID: " + user.getId() + "\tName: " + user.getFirstName() + "\tLastName: " + user.getLastName());
            modelContacts.addElement(new ContactItem(user, topMessages.get(i)));
        }

        view.showDialogs(modelContacts);


//        Thread t1 = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true) {
//                    try {
//                        Thread.sleep(5000);
//                        handle(2, "hello");
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });
//        t1.start();
    }


    public void messagesScrollEnd() {
        JScrollBar verticalBar = view.getMessageListScrollPane().getVerticalScrollBar();
        int width = view.getMessageListScrollPane().getWidth();
        int height = view.getMessageListScrollPane().getHeight();
        int widthList = view.getMessagesJList().getWidth();
        int heightList = view.getMessagesJList().getHeight();
        //view.getMessagesJList().scrollRectToVisible(new Rectangle(width, height));
        verticalBar.revalidate();
        verticalBar.setValue(verticalBar.getMaximum());
        //view.getMessageListScrollPane().getVerticalScrollBar().setValue(view.getMessageListScrollPane().getVerticalScrollBar().getMaximum());
    }


    public User getSelfUser() {
        return user;
    }


    /**
     * Обработка события выбора контакта из списка контактов
     * Установка в модель списка сообщений новые сообщения
     */
    @Override
    public Object handle(int i, String s) {
        TLPeerUser tlPeerUser = new TLPeerUser(getSelfUser().getId());
        TLMessage tlMessage = new TLMessage(0, i, tlPeerUser, true, true, DateConverter.getDateInt(), s, null);
        Message msg = new Message(tlMessage);
        model.addMessage(i, msg);
        ContactItem selected = view.getContactListRenderer().getSelectedItem();
        if (selected == null)
            return null;
        if (i == selected.getUser().getId()) {
            // TODO обновить список сообщений текущего контакта
            ((DefaultListModel<MessageItem>) view.getMessagesJList().getModel()).addElement(new MessageItem(msg));
            messagesScrollEnd();
        } else {

            DefaultListModel<ContactItem> listModel = (DefaultListModel<ContactItem>)view.getContactsJList().getModel();
            for (int k = 0; k < listModel.size(); k++) {
                ContactItem item = listModel.get(k);
                if (item.getUser().getId() == i) {
                    // TODO установить метку непрочитанного сообщения
                    item.incrementUnread();
                    // TODO установить последнее сообщение
                    item.setLastMsg(s);
                    // TODO установить дату сообщения
                    item.setLastMsgDate(DateConverter.convertIntDateToStringShort(DateConverter.getDateInt()));
                    view.getContactsJList().revalidate();
                    view.getContactsJList().repaint();
                }
            }

        }
        return null;
    }

    public void sendMessage(int userId, String message) {

        // Формироване сообщения
        int messageId = random.nextInt();
        // Отправка сообщения
        MessagesSentMessage sent = model.sendMessage(userId, message, messageId);
        // Создание локального сообщения
        TLPeerUser tlPeerUser = new TLPeerUser(getSelfUser().getId());
        TLMessage tlMessage = new TLMessage(sent.getId(), userId, tlPeerUser, true, true, DateConverter.getDateInt(), message, null);
        Message msg = new Message(tlMessage);

        // добавляем локальное сообщение в модель списка
        view.getModelMessages().addElement(new MessageItem(msg));
        // добавляем локальное сообщение в хранилище
        model.addMessage(userId, msg);

        updateDialogsOrder(msg);

        messagesScrollEnd();
        view.clearMessageTextField();
    }

    public void updateChat(User user) {
        view.updateContactLabel(user.getFirstName() + " " + user.getLastName());

        // TODO возвратить модель, заполненную сообщениями в обратном порядке
        int userId = user.getId();
        LinkedList<Message> messages = model.getMessageHistoryByUserID(userId);

        Collections.reverse(messages);
        DefaultListModel<MessageItem> model = new DefaultListModel<>();
        for (Message msg : messages) {
            model.addElement(new MessageItem(msg));
        }
        view.showMessages(model);
    }

    private void updateDialogsOrder(Message msg) {
        //    IContact contactAddLastMessage = contactList.get(contactId);
//
//      if (contactsListModel.contains(contactAddLastMessage)) {
//        int index = contactsListModel.indexOf(contactAddLastMessage);
//        IContact replaceContact = contactsListModel.get(index);
//        replaceContact.setLastMessage(newMessage);
//        //remove and set to top
//        contactsListModel.remove(index);
//        contactsListModel.add(0, replaceContact);
        DefaultListModel<ContactItem> contactModel = view.getModelContacts();
        int index = contactModel.indexOf()
    }
}
