(() => {
    "use strict";

    const app = document.getElementById("chatApp");
    if (!app) return;

    const mode = app.dataset.mode;
    const currentUserId = Number(app.dataset.userId);
    const list = document.getElementById("conversationList");
    const messageList = document.getElementById("messageList");
    const messageInput = document.getElementById("messageInput");
    const sendButton = document.getElementById("sendButton");
    const messageForm = document.getElementById("messageForm");
    const quickReplies = document.getElementById("quickReplies");
    const errorBox = document.getElementById("chatError");
    const activeTitle = document.getElementById("activeTitle");
    const connectionStatus = document.getElementById("connectionStatus");
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    let activeConversationId = null;
    let stompClient = null;
    const conversationSubscriptions = new Map();
    const renderedMessageIds = new Set();
    let reconcilingConversations = false;
    const pendingConversationMessages = new Map();

    const showError = (message = "") => { errorBox.textContent = message; };

    async function request(url, options = {}) {
        const headers = new Headers(options.headers || {});
        headers.set("Accept", "application/json");
        if (options.body) headers.set("Content-Type", "application/json");
        if (csrfToken && csrfHeader && options.method && options.method !== "GET") {
            headers.set(csrfHeader, csrfToken);
        }
        const response = await fetch(url, { ...options, headers, credentials: "same-origin" });
        if (!response.ok) {
            const body = await response.text();
            throw new Error(body || `Yêu cầu thất bại (${response.status}).`);
        }
        if (response.status === 204) return null;
        const type = response.headers.get("content-type") || "";
        return type.includes("application/json") ? response.json() : null;
    }

    function formatTime(value) {
        if (!value) return "";
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? "" : date.toLocaleString("vi-VN", { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit" });
    }

    function renderMessage(message) {
        if (renderedMessageIds.has(message.messageId)) return;
        renderedMessageIds.add(message.messageId);
        document.getElementById("welcomeMessage")?.remove();
        const isMine = Number(message.senderUserId) === currentUserId;
        const row = document.createElement("div");
        row.className = `message-row${isMine ? " mine" : ""}`;
        const bubble = document.createElement("article");
        bubble.className = "message-bubble";
        if (!isMine) {
            const sender = document.createElement("div");
            sender.className = "message-sender";
            sender.textContent = message.senderName || "";
            bubble.append(sender);
        }
        const content = document.createElement("div");
        content.textContent = message.content || "";
        const time = document.createElement("time");
        time.className = "message-time";
        time.textContent = formatTime(message.createdAt);
        bubble.append(content, time);
        row.append(bubble);
        messageList.append(row);
        messageList.scrollTop = messageList.scrollHeight;
    }

    function renderConversation(conversation) {
        let button = list.querySelector(`[data-conversation-id="${conversation.conversationId}"]`);
        if (!button) {
            document.getElementById("emptyConversations")?.remove();
            button = document.createElement("button");
            button.type = "button";
            button.className = "conversation-item";
            button.dataset.conversationId = conversation.conversationId;
            button.dataset.shopId = conversation.shopId;
            const avatar = document.createElement("span");
            avatar.className = "conversation-avatar";
            avatar.textContent = (conversation.otherPartyName || "?").slice(0, 1);
            const copy = document.createElement("span");
            copy.className = "conversation-copy";
            const name = document.createElement("strong");
            name.textContent = conversation.otherPartyName || "Cuộc trò chuyện";
            const preview = document.createElement("small");
            preview.className = "conversation-preview";
            copy.append(name, preview);
            button.append(avatar, copy);
            list.prepend(button);
        }
        button.dataset.lastMessageAt = conversation.lastMessageAt || "";
        button.dataset.lastMessageId = conversation.lastMessageId ?? "";
        const preview = button.querySelector(".conversation-preview");
        if (preview) preview.textContent = conversation.lastMessage || "Chưa có tin nhắn";
        if (conversation.unreadCount > 0) {
            let count = button.querySelector(".unread-count");
            if (!count) {
                count = document.createElement("span");
                count.className = "unread-count";
                button.append(count);
            }
            count.textContent = conversation.unreadCount;
        } else button.querySelector(".unread-count")?.remove();
    }

    function sortConversationsByRecentMessage() {
        const conversations = Array.from(list.querySelectorAll(".conversation-item"));
        conversations.sort((left, right) => {
            const leftTime = Date.parse(left.dataset.lastMessageAt || "") || 0;
            const rightTime = Date.parse(right.dataset.lastMessageAt || "") || 0;
            return rightTime - leftTime;
        });
        conversations.forEach(button => list.append(button));
    }

    function updateUnreadCount(conversationId) {
        const button = list.querySelector(`[data-conversation-id="${conversationId}"]`);
        if (!button) return;
        let count = button.querySelector(".unread-count");
        if (!count) {
            count = document.createElement("span");
            count.className = "unread-count";
            button.append(count);
        }
        count.textContent = String((Number(count.textContent) || 0) + 1);
    }

    function handleConversationMessage(id, message) {
        const button = list.querySelector(`[data-conversation-id="${id}"]`);
        const preview = button?.querySelector(".conversation-preview");
        if (preview) preview.textContent = message.content || "";
        if (button) {
            button.dataset.lastMessageAt = message.createdAt || button.dataset.lastMessageAt || "";
            button.dataset.lastMessageId = message.messageId ?? button.dataset.lastMessageId ?? "";
            sortConversationsByRecentMessage();
        }

        if (activeConversationId === id) {
            renderMessage(message);
            button?.querySelector(".unread-count")?.remove();
            if (Number(message.senderUserId) !== currentUserId) {
                request(`/api/chat/conversations/${id}/read`, { method: "POST" }).catch(() => {});
            }
        } else if (Number(message.senderUserId) !== currentUserId) {
            updateUnreadCount(id);
        }
    }

    function subscribeToConversation(conversationId) {
        const id = Number(conversationId);
        if (!stompClient?.connected || conversationSubscriptions.has(id)) return;
        const subscription = stompClient.subscribe(`/topic/conversations/${id}`, frame => {
            const message = JSON.parse(frame.body);
            if (reconcilingConversations) {
                const pending = pendingConversationMessages.get(id) || [];
                pending.push(message);
                pendingConversationMessages.set(id, pending);
                return;
            }
            handleConversationMessage(id, message);
        });
        conversationSubscriptions.set(id, subscription);
    }

    async function synchronizeConversations() {
        const endpoint = mode === "shop"
            ? "/api/chat/shop/conversations"
            : "/api/chat/customer/conversations";
        const conversations = await request(endpoint);
        const latestMessageIds = new Map();
        conversations.forEach(conversation => {
            renderConversation(conversation);
            latestMessageIds.set(Number(conversation.conversationId), Number(conversation.lastMessageId) || 0);
            subscribeToConversation(conversation.conversationId);
        });
        sortConversationsByRecentMessage();

        reconcilingConversations = false;
        pendingConversationMessages.forEach((messages, id) => {
            const latestMessageId = latestMessageIds.get(id) || 0;
            messages
                .filter(message => Number(message.messageId) > latestMessageId)
                .forEach(message => handleConversationMessage(id, message));
        });
        pendingConversationMessages.clear();
    }

    function applyPendingMessagesWithoutSnapshot() {
        reconcilingConversations = false;
        pendingConversationMessages.forEach((messages, id) => {
            messages.forEach(message => handleConversationMessage(id, message));
        });
        pendingConversationMessages.clear();
    }

    function setComposerEnabled(enabled) {
        messageInput.disabled = !enabled;
        sendButton.disabled = !enabled;
        quickReplies?.querySelectorAll("button").forEach(button => { button.disabled = !enabled; });
    }

    async function selectConversation(id, title) {
        activeConversationId = Number(id);
        activeTitle.textContent = title;
        showError();
        list.querySelectorAll(".conversation-item").forEach(item => item.classList.toggle("selected", Number(item.dataset.conversationId) === activeConversationId));
        app.classList.remove("mobile-list");
        setComposerEnabled(false);
        renderedMessageIds.clear();
        messageList.replaceChildren();
        try {
            const history = await request(`/api/chat/conversations/${activeConversationId}/messages`);
            history.forEach(renderMessage);
            if (history.length === 0) showWelcome();
            await request(`/api/chat/conversations/${activeConversationId}/read`, { method: "POST" });
            const selected = list.querySelector(`[data-conversation-id="${activeConversationId}"]`);
            selected?.querySelector(".unread-count")?.remove();
            if (stompClient?.connected) {
                setComposerEnabled(true);
                messageInput.focus();
            } else connectionStatus.textContent = "Đang chờ kết nối chat…";
        } catch (error) {
            showError(error.message || "Không tải được cuộc trò chuyện.");
        }
    }

    function showWelcome() {
        const welcome = document.createElement("div");
        welcome.className = "chat-welcome";
        welcome.innerHTML = '<span class="welcome-icon">✉</span><strong>Bắt đầu trò chuyện</strong><p>Hãy gửi tin nhắn đầu tiên của bạn.</p>';
        welcome.id = "welcomeMessage";
        messageList.append(welcome);
    }

    function connect() {
        if (!window.StompJs?.Client) {
            connectionStatus.textContent = "Không tải được thư viện chat realtime.";
            return;
        }
        if (!csrfToken || !csrfHeader) {
            console.error("Chat: thiếu CSRF token hoặc tên header trên trang.");
            connectionStatus.textContent = "Thiếu CSRF token; tải lại trang sau khi đăng nhập.";
            return;
        }
        const scheme = window.location.protocol === "https:" ? "wss" : "ws";
        const brokerURL = `${scheme}://${window.location.host}/ws`;
        stompClient = new window.StompJs.Client({
            brokerURL,
            connectHeaders: { [csrfHeader]: csrfToken },
            reconnectDelay: 3000,
            debug: (message) => {
                if (/^Opening Web Socket/i.test(message)) {
                    console.info("Chat STOMP: mở WebSocket", brokerURL);
                } else if (/^Web Socket Opened/i.test(message)) {
                    console.info("Chat STOMP: WebSocket đã mở");
                } else if (/^Connection closed/i.test(message)) {
                    console.warn("Chat STOMP: thư viện báo kết nối đã đóng");
                } else if (/^>>> CONNECT/.test(message)) {
                    // Chỉ ghi nhãn frame; không log header có chứa CSRF token.
                    console.info("Chat STOMP: gửi CONNECT");
                } else if (/^<<< CONNECTED/.test(message)) {
                    console.info("Chat STOMP: máy chủ chấp nhận CONNECT");
                } else if (/^<<< ERROR/.test(message)) {
                    console.error("Chat STOMP: máy chủ gửi ERROR frame");
                }
            },
            onConnect: () => {
                connectionStatus.textContent = "Đang hoạt động";
                conversationSubscriptions.clear();
                reconcilingConversations = true;
                pendingConversationMessages.clear();
                list.querySelectorAll(".conversation-item").forEach(item => {
                    subscribeToConversation(item.dataset.conversationId);
                });
                synchronizeConversations().then(() => {
                    const selected = list.querySelector(".conversation-item.selected");
                    if (selected) selectConversation(selected.dataset.conversationId, selected.querySelector("strong")?.textContent || "Trò chuyện");
                }).catch(error => {
                    applyPendingMessagesWithoutSnapshot();
                    console.warn("Không thể đồng bộ danh sách chat sau khi kết nối lại:", error);
                    const selected = list.querySelector(".conversation-item.selected");
                    if (selected) selectConversation(selected.dataset.conversationId, selected.querySelector("strong")?.textContent || "Trò chuyện");
                });
            },
            onWebSocketError: (event) => {
                console.error("Chat WebSocket error:", event?.type || "unknown", event);
                connectionStatus.textContent = "WebSocket gặp lỗi; xem Console và Network > WS.";
            },
            onWebSocketClose: (event) => {
                const details = {
                    code: event?.code ?? null,
                    reason: event?.reason || "",
                    wasClean: event?.wasClean ?? false
                };
                console.warn("Chat WebSocket closed:", details);
                connectionStatus.textContent = details.code
                    ? `Mất kết nối (${details.code}), đang thử kết nối lại…`
                    : "Mất kết nối, đang thử kết nối lại…";
            },
            onStompError: (frame) => {
                const details = {
                    message: frame?.headers?.message || "STOMP ERROR",
                    body: frame?.body || ""
                };
                console.error("Chat STOMP error:", details);
                connectionStatus.textContent = `STOMP bị từ chối: ${details.message}`;
            }
        });
        stompClient.activate();
    }

    list.addEventListener("click", event => {
        const button = event.target.closest(".conversation-item");
        if (button) selectConversation(button.dataset.conversationId, button.querySelector("strong")?.textContent || "Trò chuyện");
    });

    quickReplies?.addEventListener("click", event => {
        const reply = event.target.closest("[data-quick-reply]");
        if (!reply || messageInput.disabled) return;
        messageInput.value = reply.dataset.quickReply || "";
        messageInput.focus();
    });

    document.getElementById("startChat")?.addEventListener("click", async () => {
        const shopId = document.getElementById("shopSelect").value;
        if (!shopId) { showError("Vui lòng chọn cửa hàng."); return; }
        const button = document.getElementById("startChat");
        button.disabled = true;
        try {
            const conversation = await request("/api/chat/customer/conversations", { method: "POST", body: JSON.stringify({ shopId: Number(shopId) }) });
            renderConversation(conversation);
            sortConversationsByRecentMessage();
            subscribeToConversation(conversation.conversationId);
            selectConversation(conversation.conversationId, conversation.otherPartyName);
        } catch (error) { showError(error.message || "Không tạo được cuộc trò chuyện."); }
        finally { button.disabled = false; }
    });

    messageForm.addEventListener("submit", event => {
        event.preventDefault();
        const content = messageInput.value.trim();
        if (!content || !activeConversationId || !stompClient?.connected) return;
        try {
            stompClient.publish({ destination: `/app/chat/${activeConversationId}/send`, body: JSON.stringify({ content }) });
            messageInput.value = "";
            showError();
        } catch (error) { showError("Không gửi được tin nhắn. Vui lòng thử lại."); }
    });

    sortConversationsByRecentMessage();
    if (window.matchMedia("(max-width: 700px)").matches && !list.querySelector(".conversation-item")) app.classList.add("mobile-list");
    const first = list.querySelector(".conversation-item");
    if (first) {
        list.querySelectorAll(".conversation-item").forEach(item => item.classList.toggle("selected", item === first));
        selectConversation(first.dataset.conversationId, first.querySelector("strong")?.textContent || "Trò chuyện");
    }
    connect();
})();
