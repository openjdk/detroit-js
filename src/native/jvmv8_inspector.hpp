/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include <assert.h>
#include "v8.h"
#include "v8-inspector.h"
#include <jni.h>

#include "jvmv8_jni.hpp"
#include "jvmv8_jni_support.hpp"
#include "jvmv8_java_classes.hpp"
#include "jvmv8.hpp"

using namespace v8;

class JVMV8InspectorFrontEnd final : public v8_inspector::V8Inspector::Channel {
public:
    JVMV8InspectorFrontEnd(Isolate* isolate) : _isolate(isolate) {}

    virtual void sendResponse(int callId,
        std::unique_ptr<v8_inspector::StringBuffer> message) override {
        JVMV8IsolateData::inspectorSendResponse(_isolate, callId, toV8String(message->string()));
    }

    virtual void sendNotification(
        std::unique_ptr<v8_inspector::StringBuffer> message) override {
        JVMV8IsolateData::inspectorSendNotification(_isolate, toV8String(message->string()));
    }

    virtual void flushProtocolNotifications() override {
    }

private:
    inline Local<String> toV8String(const v8_inspector::StringView& strView) {
        if (!strView.length()) return String::Empty(_isolate);

        MaybeLocal<String> string;

        if (strView.is8Bit()) {
            string = String::NewFromOneByte(
                _isolate, reinterpret_cast<const uint8_t*>(strView.characters8()),
                NewStringType::kNormal, static_cast<int>(strView.length()));
        } else {
            string = String::NewFromTwoByte(
                _isolate, reinterpret_cast<const uint16_t*>(strView.characters16()),
                NewStringType::kNormal, static_cast<int>(strView.length()));
        }

        return string.IsEmpty() ? String::Empty(_isolate) : string.ToLocalChecked();
    }

    Isolate* _isolate;
};

class JVMV8InspectorClient final : public v8_inspector::V8InspectorClient {
public:
    JVMV8InspectorClient(Isolate* isolate, int contextGroupId) {
        _isolate = isolate;
        _channel.reset(new JVMV8InspectorFrontEnd(_isolate));
        _inspector = v8_inspector::V8Inspector::create(_isolate, this);
        _session = _inspector->connect(contextGroupId, _channel.get(), v8_inspector::StringView(),
                            v8_inspector::V8Inspector::kFullyTrusted);
        _contextGroupId = contextGroupId;
    }

    void contextCreated(Local<Context> context, Local<String> str) {
        int length = str->Length();
        std::unique_ptr<uint16_t[]> buffer(new uint16_t[length]);
        str->WriteV2(_isolate, 0, length, buffer.get());
        v8_inspector::StringView strView(buffer.get(), length);
        contextCreated(context, strView);
    }

    void dispatchProtocolMessage(Local<String> str) {
        int length = str->Length();
        std::unique_ptr<uint16_t[]> buffer(new uint16_t[length]);
        str->WriteV2(_isolate, 0, length, buffer.get(), 0);
        v8_inspector::StringView strView(buffer.get(), length);
        dispatchProtocolMessage(strView);
    }

    virtual void runMessageLoopOnPause(int contextGroupId) override {
        JVMV8IsolateData::inspectorRunMessageLoopOnPause(_isolate);
    }

    virtual void quitMessageLoopOnPause() override {
        JVMV8IsolateData::inspectorQuitMessageLoopOnPause(_isolate);
    }


private:

    void contextCreated(Local<Context> context, const v8_inspector::StringView& ctx_name) {
        _inspector->contextCreated(v8_inspector::V8ContextInfo(
            context, _contextGroupId, ctx_name));
    }

    void dispatchProtocolMessage(const v8_inspector::StringView& msg_view) {
        _session->dispatchProtocolMessage(msg_view);
    }

    std::unique_ptr<v8_inspector::V8Inspector> _inspector;
    std::unique_ptr<v8_inspector::V8InspectorSession> _session;
    std::unique_ptr<v8_inspector::V8Inspector::Channel> _channel;
    int _contextGroupId;
    Isolate* _isolate;
};
